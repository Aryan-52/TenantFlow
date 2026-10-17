import { useCallback, useEffect, useRef, useState, type KeyboardEvent as ReactKeyboardEvent } from 'react';
import {
  ChevronLeft,
  ChevronRight,
  Download,
  FileQuestion,
  FileText,
  FileWarning,
  Maximize2,
  Minimize2,
  MoveHorizontal,
  RefreshCw,
  Upload,
  X,
  ZoomIn,
  ZoomOut,
} from 'lucide-react';
import { attachmentApi, fileKindLabel, formatBytes, previewKind, type Attachment } from '../../api/attachments';
import { apiError } from '../../api/work';
import { useToast } from '../../contexts/ToastContext';
import { Button } from '../ui/Button';
import { loadPdf, type PDFDocumentProxy } from '../viewer/pdf';
import { PdfPages, type PdfPagesHandle } from '../viewer/PdfPages';

const TEXT_PREVIEW_LIMIT = 512 * 1024;
/** "100%" = the document's real size: 1 PDF point = 1/72 inch, the screen draws 96 px per inch. */
const ACTUAL_SIZE = 96 / 72;
const ZOOM_STEPS = [0.5, 0.67, 0.8, 0.9, 1, 1.1, 1.25, 1.5, 1.75, 2, 2.5, 3];
/** Fit-to-width never makes a page wider than this, so text stays comfortable on wide screens. */
const MAX_FIT_WIDTH = 960;
/** Space around the pages; must match `.pdf-pages` padding in features.css. */
const canvasPadding = () => (window.matchMedia?.('(max-width: 760px)').matches ? 12 : 32);

interface FilePreviewProps {
  tenantId: string;
  projectId: string;
  attachment: Attachment | null;
  onClose: () => void;
  onDownload: (a: Attachment) => void;
  /** The uploader or a workspace admin: may put back missing contents. */
  canRestore?: boolean;
  /** Called with the updated record after its contents were restored. */
  onRestored?: (a: Attachment) => void;
}

type Loaded =
  | { kind: 'pdf'; doc: PDFDocumentProxy; firstPage: { width: number; height: number } }
  | { kind: 'image'; url: string }
  | { kind: 'text'; text: string; truncated: boolean };

/**
 * TenantFlow's document viewer: a near-full-screen view on desktop and a full-screen sheet on
 * phones. PDFs are drawn page by page with PDF.js (no browser PDF toolbar), images and text
 * sit on the same neutral canvas. The bytes always come from the authorised download
 * endpoint; nothing is ever rendered as HTML. Keyed by attachment id, so opening another file
 * always starts from a clean state.
 */
export const FilePreview = (props: FilePreviewProps) =>
  props.attachment ? <DocumentViewer key={props.attachment.id} {...props} attachment={props.attachment} /> : null;

const DocumentViewer = ({ tenantId, projectId, attachment, onClose, onDownload, canRestore, onRestored }: FilePreviewProps & { attachment: Attachment }) => {
  const toast = useToast();
  const kind = previewKind(attachment.contentType);
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [error, setError] = useState('');
  const [missing, setMissing] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [restoring, setRestoring] = useState(false);
  // null = fit to width; otherwise a PDF.js scale.
  const [zoom, setZoom] = useState<number | null>(null);
  const [page, setPage] = useState(1);
  const [pageInput, setPageInput] = useState('1');
  const rootRef = useRef<HTMLDivElement>(null);
  const [scroller, setScroller] = useState<HTMLDivElement | null>(null);
  const pagesRef = useRef<PdfPagesHandle>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const onCloseRef = useRef(onClose);
  useEffect(() => {
    onCloseRef.current = onClose;
  });

  // ---- Load the bytes through the authorised API ------------------------------------------
  useEffect(() => {
    if (!kind) return;
    const controller = new AbortController();
    let url: string | null = null;
    let doc: PDFDocumentProxy | null = null;
    attachmentApi
      .fetchBlob(tenantId, projectId, attachment.id, controller.signal)
      .then(async (blob) => {
        if (kind === 'pdf') {
          doc = await loadPdf(await blob.arrayBuffer());
          const first = (await doc.getPage(1)).getViewport({ scale: 1 });
          if (controller.signal.aborted) return;
          setLoaded({ kind: 'pdf', doc, firstPage: { width: first.width, height: first.height } });
        } else if (kind === 'text') {
          const slice = blob.size > TEXT_PREVIEW_LIMIT ? blob.slice(0, TEXT_PREVIEW_LIMIT) : blob;
          const text = await slice.text();
          if (!controller.signal.aborted) setLoaded({ kind: 'text', text, truncated: blob.size > TEXT_PREVIEW_LIMIT });
        } else {
          // Re-type the blob with the server-verified content type before showing it.
          url = URL.createObjectURL(new Blob([blob], { type: attachment.contentType }));
          if (!controller.signal.aborted) setLoaded({ kind: 'image', url });
        }
        setError('');
        setMissing(false);
      })
      .catch((err) => {
        if (controller.signal.aborted) return;
        // 404 on an existing record = its stored bytes are gone (the record itself was found).
        const status = (err as { response?: { status?: number } })?.response?.status;
        setMissing(status === 404);
        setError(status ? apiError(err, 'Could not load the preview.').message : 'This file could not be read as a valid document.');
      });
    return () => {
      controller.abort();
      if (url) URL.revokeObjectURL(url);
      void doc?.loadingTask.destroy();
    };
  }, [attachment, kind, tenantId, projectId, attempt]);

  // ---- Fit to width: follows the canvas size (window resizes, full screen, phone rotation) ----
  const [canvasWidth, setCanvasWidth] = useState(0);
  // Measured when the canvas attaches (before the first paint), so a PDF never flashes at the
  // wrong size; the observer below follows later size changes.
  const attachScroller = useCallback((el: HTMLDivElement | null) => {
    setScroller(el);
    if (el) setCanvasWidth(el.clientWidth);
  }, []);
  useEffect(() => {
    if (!scroller || typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(([entry]) => setCanvasWidth(entry.contentRect.width));
    observer.observe(scroller);
    return () => observer.disconnect();
  }, [scroller]);
  const fitScale = loaded?.kind === 'pdf' && canvasWidth > 0
    ? Math.max(0.25, Math.min(canvasWidth - canvasPadding() * 2, MAX_FIT_WIDTH) / loaded.firstPage.width)
    : 1;

  const scale = zoom ?? fitScale;
  const percent = Math.round((scale / ACTUAL_SIZE) * 100);
  const total = loaded?.kind === 'pdf' ? loaded.doc.numPages : 0;

  // Functional update: key repeat or quick clicks each move one step from the latest zoom.
  const zoomBy = (direction: 1 | -1) =>
    setZoom((z) => {
      const current = (z ?? fitScale) / ACTUAL_SIZE;
      const next = direction > 0 ? ZOOM_STEPS.find((s) => s > current + 0.01) : [...ZOOM_STEPS].reverse().find((s) => s < current - 0.01);
      return next ? next * ACTUAL_SIZE : z;
    });

  // Keep the reader on the same page when the zoom changes.
  const pageBeforeZoom = useRef(1);
  useEffect(() => {
    pageBeforeZoom.current = page;
  }, [page]);
  useEffect(() => {
    if (pageBeforeZoom.current > 1) pagesRef.current?.goTo(pageBeforeZoom.current);
  }, [scale]);

  const goTo = (n: number) => {
    if (!total) return;
    const target = Math.min(Math.max(1, n), total);
    pagesRef.current?.goTo(target);
    setPage(target);
    setPageInput(String(target));
  };

  // ---- Full screen: the viewer fills the window at once, and also asks the browser for real
  // full screen. Embedded or restricted pages may refuse (or never answer) that request; the
  // viewer still fills the window, so the button always does something.
  const [fullscreen, setFullscreen] = useState(false);
  const nativeActive = useRef(false);
  useEffect(() => {
    const sync = () => {
      if (document.fullscreenElement === rootRef.current) nativeActive.current = true;
      else if (nativeActive.current) {
        // The browser left full screen (its own Escape or exit control): leave ours too.
        nativeActive.current = false;
        setFullscreen(false);
      }
    };
    document.addEventListener('fullscreenchange', sync);
    return () => document.removeEventListener('fullscreenchange', sync);
  }, []);
  const toggleFullscreen = () => {
    if (fullscreen) {
      setFullscreen(false);
      if (document.fullscreenElement) void document.exitFullscreen?.();
    } else {
      setFullscreen(true);
      if (document.fullscreenEnabled) rootRef.current?.requestFullscreen?.().catch(() => undefined);
    }
  };

  // ---- Focus: move in, keep in, give back -------------------------------------------------
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    rootRef.current?.focus();
    const body = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = body;
      if (previous && document.contains(previous)) previous.focus?.();
    };
  }, []);

  const onKeyDown = (e: ReactKeyboardEvent<HTMLDivElement>) => {
    const typing = (e.target as HTMLElement).tagName === 'INPUT';
    if (e.key === 'Escape') {
      // In fullscreen the browser handles the first Escape (leaving fullscreen).
      if (document.fullscreenElement) return;
      e.preventDefault();
      e.stopPropagation();
      if (fullscreen) setFullscreen(false);
      else onCloseRef.current();
      return;
    }
    if (e.key === 'Tab') {
      const focusable = rootRef.current?.querySelectorAll<HTMLElement>('button:not([disabled]), input, [href], [tabindex="0"]');
      if (!focusable || focusable.length === 0) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
      return;
    }
    if (typing || e.ctrlKey || e.metaKey || e.altKey) return;
    const actions: Record<string, () => void> = {
      '+': () => zoomBy(1),
      '=': () => zoomBy(1),
      '-': () => zoomBy(-1),
      '0': () => setZoom(null),
      f: toggleFullscreen,
      ...(total
        ? {
            ArrowRight: () => goTo(page + 1),
            ArrowLeft: () => goTo(page - 1),
            Home: () => goTo(1),
            End: () => goTo(total),
          }
        : {}),
    };
    const action = actions[e.key];
    if (action) {
      e.preventDefault();
      action();
    }
  };

  const restore = async (file: File | undefined) => {
    if (!file) return;
    setRestoring(true);
    try {
      const updated = await attachmentApi.restoreContent(tenantId, projectId, attachment.id, file);
      toast.success(`Restored ${attachment.filename}`);
      onRestored?.(updated);
      setMissing(false);
      setError('');
      setLoaded(null);
      setAttempt((n) => n + 1);
    } catch (err) {
      toast.error(apiError(err, 'Could not restore the file').message);
    } finally {
      setRestoring(false);
      if (fileInput.current) fileInput.current.value = '';
    }
  };

  const zoomable = loaded?.kind === 'pdf' || loaded?.kind === 'image';
  const imageScale = zoom ? zoom / ACTUAL_SIZE : null;

  return (
    <div className={`viewer-backdrop ${fullscreen ? 'is-expanded' : ''}`.trim()} onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div
        ref={rootRef}
        className={`viewer ${fullscreen ? 'is-fullscreen' : ''}`.trim()}
        role="dialog"
        aria-modal="true"
        aria-label={`Preview of ${attachment.filename}`}
        tabIndex={-1}
        onKeyDown={onKeyDown}
      >
        <header className="viewer-toolbar">
          <div className="viewer-title">
            <span className="viewer-file-icon" aria-hidden="true"><FileText size={16} /></span>
            <div className="min-w-0">
              <h2 className="viewer-name" title={attachment.filename}>{attachment.filename}</h2>
              <p className="viewer-meta">{fileKindLabel(attachment.contentType)} · {formatBytes(attachment.sizeBytes)}</p>
            </div>
          </div>

          <div className="viewer-controls">
            {total > 0 && (
              <div className="viewer-group" role="group" aria-label="Pages">
                <button type="button" className="viewer-btn" onClick={() => goTo(page - 1)} disabled={page <= 1} aria-label="Previous page" data-tooltip="Previous page">
                  <ChevronLeft size={16} />
                </button>
                <form
                  className="viewer-page"
                  onSubmit={(e) => {
                    e.preventDefault();
                    goTo(Number(pageInput) || page);
                  }}
                >
                  <input
                    value={page === Number(pageInput) ? String(page) : pageInput}
                    onChange={(e) => setPageInput(e.target.value.replace(/\D/g, ''))}
                    onBlur={() => setPageInput(String(page))}
                    inputMode="numeric"
                    aria-label={`Page number, 1 to ${total}`}
                  />
                  <span aria-hidden="true">/ {total}</span>
                </form>
                <button type="button" className="viewer-btn" onClick={() => goTo(page + 1)} disabled={page >= total} aria-label="Next page" data-tooltip="Next page">
                  <ChevronRight size={16} />
                </button>
              </div>
            )}
            {zoomable && (
              <div className="viewer-group" role="group" aria-label="Zoom">
                <button type="button" className="viewer-btn" onClick={() => zoomBy(-1)} aria-label="Zoom out" data-tooltip="Zoom out (−)">
                  <ZoomOut size={16} />
                </button>
                <span className="viewer-zoom" aria-live="polite">{loaded?.kind === 'image' && !imageScale ? 'Fit' : `${loaded?.kind === 'image' ? Math.round((imageScale ?? 1) * 100) : percent}%`}</span>
                <button type="button" className="viewer-btn" onClick={() => zoomBy(1)} aria-label="Zoom in" data-tooltip="Zoom in (+)">
                  <ZoomIn size={16} />
                </button>
                <button
                  type="button"
                  className={`viewer-btn ${zoom === null ? 'is-active' : ''}`.trim()}
                  onClick={() => setZoom(null)}
                  aria-pressed={zoom === null}
                  aria-label="Fit to width"
                  data-tooltip="Fit to width (0)"
                >
                  <MoveHorizontal size={16} />
                </button>
              </div>
            )}
          </div>

          <div className="viewer-actions">
            <button type="button" className="viewer-btn" onClick={() => onDownload(attachment)} disabled={missing} aria-label="Download" data-tooltip="Download">
              <Download size={16} />
            </button>
            <button type="button" className="viewer-btn viewer-fullscreen-btn" onClick={toggleFullscreen} aria-label={fullscreen ? 'Exit full screen' : 'Full screen'} data-tooltip={fullscreen ? 'Exit full screen' : 'Full screen (F)'}>
              {fullscreen ? <Minimize2 size={16} /> : <Maximize2 size={16} />}
            </button>
            <span className="viewer-divider" aria-hidden="true" />
            <button type="button" className="viewer-btn" onClick={onClose} aria-label="Close preview" data-tooltip="Close (Esc)">
              <X size={18} />
            </button>
          </div>
        </header>

        <div className="viewer-canvas" ref={attachScroller} tabIndex={0} aria-label="Document">
          {missing ? (
            <div className="viewer-state">
              <span className="viewer-state-icon is-warning" aria-hidden="true"><FileWarning size={22} /></span>
              <h3>The contents of this file are no longer stored</h3>
              <p>
                The record and its history are kept. {canRestore
                  ? 'If you still have the original, upload it to restore this file in place.'
                  : 'Ask the person who uploaded it, or a workspace admin, to upload the original again.'}
              </p>
              {canRestore && (
                <>
                  <input
                    ref={fileInput}
                    type="file"
                    hidden
                    accept={attachment.contentType}
                    onChange={(e) => restore(e.target.files?.[0])}
                    aria-label={`Choose the original ${attachment.filename}`}
                  />
                  <Button variant="secondary" onClick={() => fileInput.current?.click()} isLoading={restoring}>
                    <Upload size={15} /> Upload the original again
                  </Button>
                </>
              )}
            </div>
          ) : !kind ? (
            <div className="viewer-state">
              <span className="viewer-state-icon" aria-hidden="true"><FileQuestion size={22} /></span>
              <h3>No preview for {fileKindLabel(attachment.contentType)} files</h3>
              <p>Download the file to open it in its own app.</p>
              <Button onClick={() => onDownload(attachment)}><Download size={15} /> Download</Button>
            </div>
          ) : error ? (
            <div className="viewer-state" role="alert">
              <span className="viewer-state-icon is-danger" aria-hidden="true"><FileWarning size={22} /></span>
              <h3>The preview could not be shown</h3>
              <p>{error}</p>
              <div className="flex gap-2">
                <Button variant="secondary" onClick={() => { setError(''); setAttempt((n) => n + 1); }}><RefreshCw size={15} /> Try again</Button>
                <Button variant="ghost" onClick={() => onDownload(attachment)}><Download size={15} /> Download instead</Button>
              </div>
            </div>
          ) : !loaded ? (
            <div className="viewer-loading" aria-busy="true" aria-label="Loading preview">
              <div className="viewer-sheet-skeleton" />
              <p>Opening {attachment.filename}…</p>
            </div>
          ) : loaded.kind === 'pdf' ? (
            <PdfPages ref={pagesRef} doc={loaded.doc} scale={scale} firstPage={loaded.firstPage} scroller={scroller} onPageChange={(p) => { setPage(p); setPageInput(String(p)); }} />
          ) : loaded.kind === 'image' ? (
            <div className="viewer-image-wrap">
              <img
                className={`viewer-image ${imageScale ? 'is-zoomed' : ''}`.trim()}
                src={loaded.url}
                alt={attachment.filename}
                style={imageScale ? { width: `${imageScale * 100}%`, maxWidth: 'none' } : undefined}
              />
            </div>
          ) : (
            <div className="viewer-text-sheet">
              <pre>{loaded.text}</pre>
              {loaded.truncated && <p className="viewer-note">Showing the first 512 KB. Download the file to see all of it.</p>}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
