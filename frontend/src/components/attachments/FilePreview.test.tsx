import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Attachment } from '../../api/attachments';
import { FilePreview } from './FilePreview';

const { fetchBlob, restoreContent, loadPdf } = vi.hoisted(() => ({ fetchBlob: vi.fn(), restoreContent: vi.fn(), loadPdf: vi.fn() }));
vi.mock('../../api/attachments', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/attachments')>()),
  attachmentApi: { fetchBlob, restoreContent },
}));
vi.mock('../viewer/pdf', () => ({ loadPdf }));

/** A stand-in for a PDF.js document: A4-ish pages that "render" instantly. */
const fakePdf = (numPages: number) => {
  const render = vi.fn(() => ({ promise: Promise.resolve(), cancel: vi.fn() }));
  const page = { getViewport: ({ scale }: { scale: number }) => ({ width: 600 * scale, height: 800 * scale }), render };
  return { numPages, getPage: vi.fn(() => Promise.resolve(page)), loadingTask: { destroy: vi.fn(() => Promise.resolve()) }, render };
};

const file = (id: string, filename: string, contentType: string): Attachment => ({
  id, projectId: 'p1', taskId: null, filename, contentType, sizeBytes: 1000, uploadedById: 'u1', uploadedByName: 'Ada',
  createdAt: '2026-01-01T00:00:00Z',
});
const pdf = file('a1', 'receipt.pdf', 'application/pdf');
const pdfBlob = () => new Blob(['%PDF-1.4'], { type: 'application/pdf' });
const missing = { response: { status: 404, data: { message: "This file's contents are no longer available in storage." } } };
const open = (extra: Partial<Parameters<typeof FilePreview>[0]> = {}) => {
  const props = { tenantId: 't1', projectId: 'p1', attachment: pdf, onClose: vi.fn(), onDownload: vi.fn(), ...extra };
  return { ...render(<FilePreview {...props} />), props };
};

// jsdom has no layout, canvas or fullscreen: give pages a position and stub the rest.
const scrollTo = vi.fn();
const offsetTop = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'offsetTop')!;
beforeAll(() => {
  Object.defineProperty(HTMLElement.prototype, 'offsetTop', {
    configurable: true,
    get() { return ((Number((this as HTMLElement).dataset.page) || 1) - 1) * 1000; },
  });
  HTMLElement.prototype.scrollTo = scrollTo as unknown as typeof HTMLElement.prototype.scrollTo;
  HTMLCanvasElement.prototype.getContext = vi.fn(() => ({})) as unknown as typeof HTMLCanvasElement.prototype.getContext;
  HTMLElement.prototype.requestFullscreen = vi.fn(function (this: HTMLElement) {
    Object.defineProperty(document, 'fullscreenElement', { configurable: true, value: this });
    document.dispatchEvent(new Event('fullscreenchange'));
    return Promise.resolve();
  });
  Object.defineProperty(document, 'fullscreenEnabled', { configurable: true, value: true });
  document.exitFullscreen = vi.fn(() => {
    Object.defineProperty(document, 'fullscreenElement', { configurable: true, value: null });
    document.dispatchEvent(new Event('fullscreenchange'));
    return Promise.resolve();
  });
});
afterAll(() => {
  Object.defineProperty(HTMLElement.prototype, 'offsetTop', offsetTop);
});

describe('FilePreview', () => {
  beforeEach(() => {
    fetchBlob.mockReset();
    restoreContent.mockReset();
    loadPdf.mockReset();
    scrollTo.mockReset();
    globalThis.URL.createObjectURL = vi.fn(() => 'blob:preview');
    globalThis.URL.revokeObjectURL = vi.fn();
  });

  it('draws every PDF page itself (no embedded browser viewer) from the authorised download', async () => {
    const doc = fakePdf(3);
    fetchBlob.mockResolvedValue(pdfBlob());
    loadPdf.mockResolvedValue(doc);
    const { container } = open();
    expect(screen.getByLabelText('Loading preview')).toBeTruthy();
    expect(await screen.findByRole('img', { name: 'Page 1 of 3' })).toBeTruthy();
    expect(screen.getAllByRole('img', { name: /Page \d of 3/ })).toHaveLength(3);
    expect(container.querySelector('iframe, embed, object')).toBeNull();
    expect(fetchBlob).toHaveBeenCalledWith('t1', 'p1', 'a1', expect.any(AbortSignal));
    expect(loadPdf).toHaveBeenCalledWith(expect.any(ArrayBuffer));
    // Pages are drawn after an asynchronous getPage(): wait for it rather than racing it.
    await waitFor(() => expect(doc.render).toHaveBeenCalled());
    expect(screen.getByRole('dialog', { name: 'Preview of receipt.pdf' })).toBeTruthy();
  });

  it('moves between pages with the buttons, the page field and the keyboard', async () => {
    const user = userEvent.setup();
    fetchBlob.mockResolvedValue(pdfBlob());
    loadPdf.mockResolvedValue(fakePdf(3));
    open();
    const field = await screen.findByRole('textbox', { name: 'Page number, 1 to 3' }) as HTMLInputElement;
    expect(field.value).toBe('1');
    expect((screen.getByRole('button', { name: 'Previous page' }) as HTMLButtonElement).disabled).toBe(true);

    await user.click(screen.getByRole('button', { name: 'Next page' }));
    expect(field.value).toBe('2');
    expect(scrollTo).toHaveBeenLastCalledWith({ top: 1000 - 16 });

    await user.clear(field);
    await user.type(field, '3{Enter}');
    expect(field.value).toBe('3');
    expect((screen.getByRole('button', { name: 'Next page' }) as HTMLButtonElement).disabled).toBe(true);

    screen.getByRole('dialog').focus();
    await user.keyboard('{Home}');
    expect(field.value).toBe('1');
    await user.keyboard('{End}');
    expect(field.value).toBe('3');
    await user.keyboard('{ArrowLeft}');
    expect(field.value).toBe('2');
  });

  it('zooms in and out, and returns to fit-to-width', async () => {
    const user = userEvent.setup();
    fetchBlob.mockResolvedValue(pdfBlob());
    loadPdf.mockResolvedValue(fakePdf(1));
    open();
    const fit = await screen.findByRole('button', { name: 'Fit to width' });
    expect(fit.getAttribute('aria-pressed')).toBe('true');
    const page = screen.getByRole('img', { name: 'Page 1 of 1' });
    const zoomGroup = screen.getByRole('group', { name: 'Zoom' });

    await user.click(screen.getByRole('button', { name: 'Zoom in' }));
    expect(fit.getAttribute('aria-pressed')).toBe('false');
    const zoomed = within(zoomGroup).getByText(/%$/).textContent;
    // 100% = real size: 600pt wide = 800 CSS px.
    await user.click(screen.getByRole('button', { name: 'Zoom in' }));
    expect(within(zoomGroup).getByText(/%$/).textContent).not.toBe(zoomed);

    screen.getByRole('dialog').focus();
    await user.keyboard('-');
    expect(within(zoomGroup).getByText(/%$/).textContent).toBe(zoomed);

    await user.keyboard('0');
    expect(fit.getAttribute('aria-pressed')).toBe('true');
    expect(page.style.width).toBeTruthy();
  });

  it('opens PDFs fitted to the width of the viewer, without waiting for a resize event', async () => {
    const clientWidth = vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockImplementation(function (this: HTMLElement) {
      return this.classList.contains('viewer-canvas') ? 364 : 0;
    });
    fetchBlob.mockResolvedValue(pdfBlob());
    loadPdf.mockResolvedValue(fakePdf(1));
    open();
    // 364px canvas - 2 x 32px padding = 300px for a 600pt-wide page: scale 0.5 = 38% of real size.
    const page = await screen.findByRole('img', { name: 'Page 1 of 1' });
    expect(page.style.width).toBe('300px');
    expect(within(screen.getByRole('group', { name: 'Zoom' })).getByText('38%')).toBeTruthy();
    clientWidth.mockRestore();
  });

  it('downloads, enters full screen and closes with Escape or the close button', async () => {
    const user = userEvent.setup();
    fetchBlob.mockResolvedValue(pdfBlob());
    loadPdf.mockResolvedValue(fakePdf(1));
    const { props } = open();
    await screen.findByRole('img', { name: 'Page 1 of 1' });

    await user.click(screen.getByRole('button', { name: 'Download' }));
    expect(props.onDownload).toHaveBeenCalledWith(pdf);

    await user.click(screen.getByRole('button', { name: 'Full screen' }));
    expect(screen.getByRole('dialog').className).toContain('is-fullscreen');
    await user.click(screen.getByRole('button', { name: 'Exit full screen' }));
    expect(screen.getByRole('dialog').className).not.toContain('is-fullscreen');

    // Where the browser refuses real full screen (e.g. inside an embedding page), the viewer
    // still fills the window, and Escape first leaves that mode rather than closing the viewer.
    vi.mocked(HTMLElement.prototype.requestFullscreen).mockRejectedValueOnce(new TypeError('Permissions check failed'));
    await user.click(screen.getByRole('button', { name: 'Full screen' }));
    expect(screen.getByRole('dialog').className).toContain('is-fullscreen');
    screen.getByRole('dialog').focus();
    await user.keyboard('{Escape}');
    expect(screen.getByRole('dialog').className).not.toContain('is-fullscreen');
    expect(props.onClose).not.toHaveBeenCalled();

    screen.getByRole('dialog').focus();
    await user.keyboard('{Escape}');
    expect(props.onClose).toHaveBeenCalledTimes(1);
    await user.click(screen.getByRole('button', { name: 'Close preview' }));
    expect(props.onClose).toHaveBeenCalledTimes(2);
  });

  it('explains a file PDF.js cannot read instead of showing a blank page', async () => {
    fetchBlob.mockResolvedValue(pdfBlob());
    loadPdf.mockRejectedValue(new Error('Invalid PDF structure'));
    open();
    expect(await screen.findByText('This file could not be read as a valid document.')).toBeTruthy();
    expect(screen.getByRole('button', { name: /Try again/ })).toBeTruthy();
  });

  it('explains missing contents, keeps the record, and lets the uploader restore them in place', async () => {
    const user = userEvent.setup();
    const onRestored = vi.fn();
    fetchBlob.mockRejectedValueOnce(missing).mockResolvedValueOnce(pdfBlob());
    loadPdf.mockResolvedValue(fakePdf(1));
    restoreContent.mockResolvedValue(pdf);
    open({ canRestore: true, onRestored });
    expect(await screen.findByText('The contents of this file are no longer stored')).toBeTruthy();
    expect(screen.getByText(/The record and its history are kept/)).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Download' }) as HTMLButtonElement).disabled).toBe(true);

    const original = new File(['%PDF-1.4'], 'receipt.pdf', { type: 'application/pdf' });
    await user.upload(screen.getByLabelText('Choose the original receipt.pdf'), original);
    expect(restoreContent).toHaveBeenCalledWith('t1', 'p1', 'a1', original);
    expect(onRestored).toHaveBeenCalledWith(pdf);
    expect(await screen.findByRole('img', { name: 'Page 1 of 1' })).toBeTruthy();
  });

  it('people who cannot restore are told who can', async () => {
    fetchBlob.mockRejectedValue(missing);
    open();
    expect(await screen.findByText(/Ask the person who uploaded it, or a workspace admin/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Upload the original again/ })).toBeNull();
  });

  it('starts fresh when another file is opened (no stale error or preview)', async () => {
    fetchBlob.mockRejectedValueOnce({ response: { status: 503, data: { message: 'Storage is temporarily unavailable' } } });
    const { rerender, props } = open();
    expect(await screen.findByText('Storage is temporarily unavailable')).toBeTruthy();
    fetchBlob.mockResolvedValueOnce(new Blob(['hello']));
    rerender(<FilePreview {...props} attachment={file('a2', 'notes.txt', 'text/plain')} />);
    expect(screen.queryByText('Storage is temporarily unavailable')).toBeNull();
    expect(await screen.findByText('hello')).toBeTruthy();
  });

  it('gives focus back to what opened it when closed', async () => {
    fetchBlob.mockResolvedValue(new Blob(['hello']));
    const opener = document.createElement('button');
    document.body.appendChild(opener);
    opener.focus();
    const { unmount } = open({ attachment: file('a3', 'notes.txt', 'text/plain') });
    await screen.findByText('hello');
    expect(document.activeElement).toBe(screen.getByRole('dialog'));
    unmount();
    expect(document.activeElement).toBe(opener);
    opener.remove();
  });
});
