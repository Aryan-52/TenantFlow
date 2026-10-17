import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Attachment } from '../../api/attachments';
import { AttachmentsPanel } from './AttachmentsPanel';

const { list, upload, download, remove } = vi.hoisted(() => ({
  list: vi.fn(),
  upload: vi.fn(),
  download: vi.fn(),
  remove: vi.fn(),
}));

vi.mock('../../api/attachments', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/attachments')>();
  return { ...actual, attachmentApi: { list, upload, download, remove } };
});

vi.mock('../../contexts/AuthContext', () => ({
  useAuth: () => ({ user: { id: 'me', name: 'Me', email: 'me@example.test' } }),
}));

const file = (id: string, uploadedById: string, filename = `${id}.pdf`): Attachment => ({
  id,
  projectId: 'p1',
  taskId: 't1',
  filename,
  contentType: 'application/pdf',
  sizeBytes: 2048,
  uploadedById,
  uploadedByName: uploadedById === 'me' ? 'Me' : 'Someone',
  createdAt: new Date().toISOString(),
});

const page = (items: Attachment[]) => ({ items, page: 0, size: 50, totalElements: items.length, totalPages: 1, hasNext: false });

describe('AttachmentsPanel', () => {
  beforeEach(() => {
    list.mockReset().mockResolvedValue(page([file('mine', 'me'), file('theirs', 'other')]));
    upload.mockReset();
    download.mockReset().mockResolvedValue(undefined);
    remove.mockReset().mockResolvedValue(undefined);
  });

  it('lists files for the task and only offers delete on your own files to members', async () => {
    render(<AttachmentsPanel tenantId="tn" projectId="p1" taskId="t1" canManage={false} />);
    expect(await screen.findByText('mine.pdf')).toBeTruthy();
    expect(list).toHaveBeenCalledWith('tn', 'p1', { taskId: 't1', size: 50 });
    expect(screen.getByText(/2\.0 KB · Me/)).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Delete mine.pdf' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Delete theirs.pdf' })).toBeNull();
  });

  it('lets workspace admins delete any file, after confirmation', async () => {
    const user = userEvent.setup();
    render(<AttachmentsPanel tenantId="tn" projectId="p1" taskId="t1" canManage />);
    await user.click(await screen.findByRole('button', { name: 'Delete theirs.pdf' }));
    await user.click(await screen.findByRole('button', { name: 'Delete file' }));
    expect(remove).toHaveBeenCalledWith('tn', 'p1', 'theirs');
    await waitFor(() => expect(screen.queryByText('theirs.pdf')).toBeNull());
  });

  it('rejects a disallowed type before uploading', async () => {
    const user = userEvent.setup({ applyAccept: false });
    render(<AttachmentsPanel tenantId="tn" projectId="p1" canManage={false} />);
    await screen.findByText('mine.pdf');
    await user.upload(screen.getByLabelText('Choose a file to upload'), new File(['<html>'], 'page.html', { type: 'text/html' }));
    expect(await screen.findByRole('alert')).toHaveProperty('textContent', expect.stringContaining('not an allowed file type'));
    expect(upload).not.toHaveBeenCalled();
  });

  it('uploads with progress and shows the new file', async () => {
    const user = userEvent.setup();
    upload.mockImplementation(async (_t: string, _p: string, _f: File, _task: string | undefined, onProgress: (n: number) => void) => {
      onProgress(50);
      return file('new', 'me', 'report.pdf');
    });
    render(<AttachmentsPanel tenantId="tn" projectId="p1" taskId="t1" canManage={false} />);
    await screen.findByText('mine.pdf');
    await user.upload(screen.getByLabelText('Choose a file to upload'), new File(['%PDF-1.7'], 'report.pdf', { type: 'application/pdf' }));
    expect(await screen.findByText('report.pdf')).toBeTruthy();
    expect(upload.mock.calls[0][3]).toBe('t1');
  });

  it('downloads through the API and shows server errors', async () => {
    const user = userEvent.setup();
    render(<AttachmentsPanel tenantId="tn" projectId="p1" taskId="t1" canManage={false} />);
    await user.click(await screen.findByRole('button', { name: 'Download mine.pdf' }));
    expect(download).toHaveBeenCalledWith('tn', 'p1', expect.objectContaining({ id: 'mine' }));

    download.mockRejectedValueOnce({ response: { data: { message: 'File storage is temporarily unavailable. Please try again.' } } });
    await user.click(screen.getByRole('button', { name: 'Download theirs.pdf' }));
    expect(await screen.findByText(/File storage is temporarily unavailable/)).toBeTruthy();
  });
});
