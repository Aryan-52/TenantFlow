import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from 'react';
import type { PDFDocumentProxy } from './pdf';

interface PageSize {
  width: number;
  height: number;
}

export interface PdfPagesHandle {
  /** Scrolls so that page n (1-based) starts at the top of the canvas. */
  goTo: (page: number) => void;
}

interface PdfPagesProps {
  doc: PDFDocumentProxy;
  /** PDF.js scale: 1 = 1 PDF point per CSS pixel. */
  scale: number;
  /** Size of page 1 at scale 1, used for pages not measured yet. */
  firstPage: PageSize;
  scroller: HTMLElement | null;
  onPageChange: (page: number) => void;
}

/** Pages within this distance of the visible area are drawn; farther ones are released. */
const RENDER_MARGIN = '800px 0px';

/**
 * Every page as a white sheet in one vertical column. Each page draws its canvas only while
 * it is on or near the screen (and releases it when it scrolls far away), so long documents
 * stay light. Canvases are sized for the device pixel ratio so text stays crisp.
 */
export const PdfPages = forwardRef<PdfPagesHandle, PdfPagesProps>(({ doc, scale, firstPage, scroller, onPageChange }, ref) => {
  const pageRefs = useRef<(HTMLDivElement | null)[]>([]);
  const onPageChangeRef = useRef(onPageChange);
  useEffect(() => {
    onPageChangeRef.current = onPageChange;
  });

  useImperativeHandle(ref, () => ({
    goTo: (page: number) => {
      const el = pageRefs.current[page - 1];
      if (el && scroller) scroller.scrollTo({ top: el.offsetTop - 16 });
    },
  }), [scroller]);

  // Current page = the last page whose top has passed the upper third of the canvas.
  useEffect(() => {
    if (!scroller) return;
    let frame = 0;
    const update = () => {
      frame = 0;
      const mark = scroller.scrollTop + scroller.clientHeight / 3;
      let current = 1;
      pageRefs.current.forEach((el, i) => {
        if (el && el.offsetTop <= mark) current = i + 1;
      });
      onPageChangeRef.current(current);
    };
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(update);
    };
    scroller.addEventListener('scroll', onScroll, { passive: true });
    update();
    return () => {
      scroller.removeEventListener('scroll', onScroll);
      if (frame) cancelAnimationFrame(frame);
    };
  }, [scroller, scale]);

  return (
    <div className="pdf-pages">
      {Array.from({ length: doc.numPages }, (_, i) => (
        <PdfPage
          key={i}
          ref={(el) => {
            pageRefs.current[i] = el;
          }}
          doc={doc}
          pageNumber={i + 1}
          total={doc.numPages}
          scale={scale}
          fallbackSize={firstPage}
          scroller={scroller}
        />
      ))}
    </div>
  );
});
PdfPages.displayName = 'PdfPages';

interface PdfPageProps {
  doc: PDFDocumentProxy;
  pageNumber: number;
  total: number;
  scale: number;
  fallbackSize: PageSize;
  scroller: HTMLElement | null;
}

const PdfPage = forwardRef<HTMLDivElement, PdfPageProps>(({ doc, pageNumber, total, scale, fallbackSize, scroller }, ref) => {
  const holderRef = useRef<HTMLDivElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [near, setNear] = useState(pageNumber === 1);
  const [size, setSize] = useState<PageSize | null>(null);
  const [failed, setFailed] = useState(false);

  // Draw only when the page is on or near the screen.
  useEffect(() => {
    const el = holderRef.current;
    if (!el || typeof IntersectionObserver === 'undefined') {
      setNear(true);
      return;
    }
    const observer = new IntersectionObserver(([entry]) => setNear(entry.isIntersecting), { root: scroller, rootMargin: RENDER_MARGIN });
    observer.observe(el);
    return () => observer.disconnect();
  }, [scroller]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!near || !canvas) {
      // Far away: release the bitmap memory.
      if (canvas) {
        canvas.width = 0;
        canvas.height = 0;
      }
      return;
    }
    let cancelled = false;
    let task: { cancel: () => void; promise: Promise<unknown> } | null = null;
    doc.getPage(pageNumber).then((page) => {
      if (cancelled) return;
      const base = page.getViewport({ scale: 1 });
      setSize({ width: base.width, height: base.height });
      const ratio = Math.min(window.devicePixelRatio || 1, 3);
      const viewport = page.getViewport({ scale: scale * ratio });
      const context = canvas.getContext('2d');
      if (!context) return;
      canvas.width = Math.floor(viewport.width);
      canvas.height = Math.floor(viewport.height);
      task = page.render({ canvas, canvasContext: context, viewport });
      task.promise.catch((err: { name?: string }) => {
        if (!cancelled && err?.name !== 'RenderingCancelledException') setFailed(true);
      });
    }).catch(() => !cancelled && setFailed(true));
    return () => {
      cancelled = true;
      task?.cancel();
    };
  }, [doc, pageNumber, scale, near]);

  const box = size ?? fallbackSize;
  return (
    <div
      ref={(el) => {
        holderRef.current = el;
        if (typeof ref === 'function') ref(el);
        else if (ref) ref.current = el;
      }}
      className="pdf-page"
      style={{ width: Math.floor(box.width * scale), height: Math.floor(box.height * scale) }}
      role="img"
      aria-label={`Page ${pageNumber} of ${total}`}
      data-page={pageNumber}
    >
      <canvas ref={canvasRef} className="pdf-canvas" />
      {failed && <p className="pdf-page-error">This page could not be displayed.</p>}
    </div>
  );
});
PdfPage.displayName = 'PdfPage';
