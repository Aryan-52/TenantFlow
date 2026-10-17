import type { PDFDocumentProxy } from 'pdfjs-dist';

/**
 * Loads a PDF with PDF.js. The library (and its web worker) are fetched only the first time
 * a PDF is opened, so they never weigh on the rest of the app.
 *
 * The bytes come from TenantFlow's authorised download endpoint; PDF.js only parses them.
 * XFA forms are not rendered; PDF.js never runs scripts embedded in a PDF.
 */
export async function loadPdf(data: ArrayBuffer): Promise<PDFDocumentProxy> {
  const [pdfjs, worker] = await Promise.all([
    import('pdfjs-dist'),
    import('pdfjs-dist/build/pdf.worker.min.mjs?url'),
  ]);
  pdfjs.GlobalWorkerOptions.workerSrc = worker.default;
  return pdfjs.getDocument({ data, enableXfa: false }).promise;
}

export type { PDFDocumentProxy };
