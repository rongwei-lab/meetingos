/**
 * html2canvas 和 jspdf 的类型声明
 * 当依赖安装后，这些声明会被实际的类型定义覆盖
 */

declare module 'html2canvas' {
  interface Options {
    backgroundColor?: string | null;
    scale?: number;
    useCORS?: boolean;
    logging?: boolean;
    windowWidth?: number;
    windowHeight?: number;
  }

  function html2canvas(element: HTMLElement, options?: Options): Promise<HTMLCanvasElement>;
  
  export default html2canvas;
}

declare module 'jspdf' {
  type Orientation = 'portrait' | 'landscape';
  type Unit = 'pt' | 'px' | 'mm' | 'cm' | 'in';
  type Format = 'a0' | 'a1' | 'a2' | 'a3' | 'a4' | 'a5' | 'a6' | 'a7' | 'a8' | 'a9' | 'a10';

  interface jsPDFOptions {
    orientation?: Orientation;
    unit?: Unit;
    format?: Format | number[];
    compress?: boolean;
  }

  export default class jsPDF {
    constructor(options?: jsPDFOptions);
    addImage(
      imageData: string | HTMLImageElement | HTMLCanvasElement | Uint8Array,
      format: string,
      x: number,
      y: number,
      width: number,
      height: number,
      alias?: string,
      compression?: 'NONE' | 'FAST' | 'MEDIUM' | 'SLOW',
      rotation?: number
    ): jsPDF;
    save(filename: string): jsPDF;
  }
}