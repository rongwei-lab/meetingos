/**
 * 导出格式类型
 */
export type ExportFormat = 'png' | 'jpeg' | 'pdf';

/**
 * 导出配置选项
 */
export interface ExportOptions {
  /** 导出格式 */
  format: ExportFormat;
  /** 文件名（不含扩展名） */
  filename?: string;
  /** 图片质量（仅 JPEG 格式，0-1） */
  quality?: number;
  /** PDF 方向 */
  orientation?: 'portrait' | 'landscape';
  /** 是否包含座位统计信息 */
  includeStats?: boolean;
}

interface ExportBox {
  width: number;
  height: number;
}

const EXPORT_STYLE_PROPERTIES = ['overflow', 'max-height', 'width', 'height', 'min-width', 'min-height'] as const;

function nextFrame(): Promise<void> {
  return new Promise((resolve) => {
    window.requestAnimationFrame(() => resolve());
  });
}

function snapshotInlineStyles(element: HTMLElement, properties: readonly string[]): () => void {
  const values = properties.map((property) => [property, element.style.getPropertyValue(property)] as const);
  return () => {
    values.forEach(([property, value]) => {
      if (value) {
        element.style.setProperty(property, value);
      } else {
        element.style.removeProperty(property);
      }
    });
  };
}

function resolveExportBox(element: HTMLElement): ExportBox {
  const rect = element.getBoundingClientRect();
  return {
    /*
     * html2canvas 默认会按元素的可视盒子裁剪，滚动容器里超出当前视口的座位就会丢失。
     * 这里显式取 scroll/offset/实际渲染尺寸的最大值，保证横向和纵向滚动内容都进入画布。
     */
    width: Math.ceil(Math.max(element.scrollWidth, element.offsetWidth, rect.width)),
    height: Math.ceil(Math.max(element.scrollHeight, element.offsetHeight, rect.height))
  };
}

/**
 * 将 HTML 元素导出为图片或 PDF
 * 
 * @param element 要导出的 DOM 元素
 * @param options 导出配置
 * @returns Promise
 */
export async function exportSeatingChart(
  element: HTMLElement,
  options: ExportOptions
): Promise<void> {
  const {
    format,
    filename = `座位表_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '-')}`,
    quality = 0.95,
    orientation = 'landscape',
  } = options;

  try {
    /*
     * html2canvas 和 jsPDF 体积较大，仅在用户真正执行导出时加载。
     * 避免进入座位页或快速切换菜单时解析导出库，加重浏览器主线程压力。
     */
    const [{ default: html2canvas }] = await Promise.all([import('html2canvas')]);

    await nextFrame();
    const exportBox = resolveExportBox(element);

    // 使用 html2canvas 将 DOM 转为 canvas。width/height 必须显式传入，否则滚动容器会只导出可见区域。
    const canvas = await html2canvas(element, {
      backgroundColor: '#ffffff',
      scale: 2, // 提高分辨率，2倍清晰度
      useCORS: true, // 支持跨域图片
      logging: false,
      width: exportBox.width,
      height: exportBox.height,
      windowWidth: exportBox.width,
      windowHeight: exportBox.height,
      scrollX: 0,
      scrollY: 0,
    });

    if (format === 'pdf') {
      // 导出为 PDF
      await exportToPDF(canvas, filename, orientation);
    } else {
      // 导出为图片（PNG 或 JPEG）
      await exportToImage(canvas, filename, format, quality);
    }
  } catch (error) {
    console.error('导出座位表失败:', error);
    throw new Error(`导出失败: ${error instanceof Error ? error.message : '未知错误'}`);
  }
}

/**
 * 将 canvas 导出为图片文件
 * 
 * @param canvas Canvas 元素
 * @param filename 文件名
 * @param format 图片格式
 * @param quality 图片质量
 */
function exportToImage(
  canvas: HTMLCanvasElement,
  filename: string,
  format: 'png' | 'jpeg',
  quality: number
): Promise<void> {
  const mimeType = format === 'png' ? 'image/png' : 'image/jpeg';
  const extension = format === 'png' ? 'png' : 'jpg';

  return new Promise((resolve, reject) => {
    // 将 canvas 转为 blob。toBlob 是回调式 API，必须转成 Promise 才能把失败正确抛给页面提示。
    canvas.toBlob(
      (blob) => {
        if (!blob) {
          reject(new Error('Canvas 转换失败'));
          return;
        }

        // 创建下载链接
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `${filename}.${extension}`;

        // 触发下载
        document.body.appendChild(link);
        link.click();

        // 清理
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
        resolve();
      },
      mimeType,
      quality
    );
  });
}

/**
 * 将 canvas 导出为 PDF 文件
 * 
 * @param canvas Canvas 元素
 * @param filename 文件名
 * @param orientation PDF 方向
 */
async function exportToPDF(
  canvas: HTMLCanvasElement,
  filename: string,
  orientation: 'portrait' | 'landscape'
): Promise<void> {
  const { default: jsPDF } = await import('jspdf');

  // 计算 PDF 尺寸（A4 纸张，单位 mm）
  const pdfWidth = orientation === 'portrait' ? 210 : 297;
  const pdfHeight = orientation === 'portrait' ? 297 : 210;
  const margin = 8;
  const availableWidth = pdfWidth - margin * 2;
  const availableHeight = pdfHeight - margin * 2;
  const imgPdfWidth = availableWidth;
  const imgPdfHeight = canvas.height * (imgPdfWidth / canvas.width);

  // 创建 PDF 文档
  const pdf = new jsPDF({
    orientation,
    unit: 'mm',
    format: 'a4',
    compress: true,
  });

  if (imgPdfHeight <= availableHeight) {
    const marginY = (pdfHeight - imgPdfHeight) / 2;
    const imgData = canvas.toDataURL('image/jpeg', 0.95);
    pdf.addImage(imgData, 'JPEG', margin, marginY, imgPdfWidth, imgPdfHeight);
    pdf.save(`${filename}.pdf`);
    return;
  }

  /*
   * 大会场座位图通常是“很宽且很长”的画布。直接压进一页 A4 会导致内容过小，
   * 也更容易让浏览器/PDF 阅读器在底部裁剪；按画布像素切片分页可确保所有区域都导出。
   */
  const pageCanvasHeight = Math.floor(canvas.width * (availableHeight / imgPdfWidth));
  const pageCanvas = document.createElement('canvas');
  pageCanvas.width = canvas.width;
  const pageContext = pageCanvas.getContext('2d');
  if (!pageContext) {
    throw new Error('PDF 分页画布创建失败');
  }

  let offsetY = 0;
  let pageIndex = 0;
  while (offsetY < canvas.height) {
    const sliceHeight = Math.min(pageCanvasHeight, canvas.height - offsetY);
    pageCanvas.height = sliceHeight;
    pageContext.fillStyle = '#ffffff';
    pageContext.fillRect(0, 0, pageCanvas.width, pageCanvas.height);
    pageContext.drawImage(canvas, 0, offsetY, canvas.width, sliceHeight, 0, 0, canvas.width, sliceHeight);

    if (pageIndex > 0) {
      pdf.addPage('a4', orientation);
    }

    const slicePdfHeight = sliceHeight * (imgPdfWidth / canvas.width);
    pdf.addImage(pageCanvas.toDataURL('image/jpeg', 0.95), 'JPEG', margin, margin, imgPdfWidth, slicePdfHeight);

    offsetY += sliceHeight;
    pageIndex += 1;
  }

  // 下载 PDF
  pdf.save(`${filename}.pdf`);
}

/**
 * 准备导出：隐藏不必要的 UI 元素
 * 
 * @param element 要导出的元素
 * @returns 清理函数
 */
export function prepareExportElement(element: HTMLElement): () => void {
  const viewport = element.querySelector<HTMLElement>('.seat-board-viewport');
  const restoreElementStyles = snapshotInlineStyles(element, EXPORT_STYLE_PROPERTIES);
  const restoreViewportStyles = viewport ? snapshotInlineStyles(viewport, EXPORT_STYLE_PROPERTIES) : undefined;
  const originalScrollLeft = element.scrollLeft;
  const originalScrollTop = element.scrollTop;

  // 隐藏控制按钮（缩放控件等），避免导出的座位图带上管理端操作 UI。
  const controls = element.querySelector('.seat-board-controls') as HTMLElement;
  const originalControlsDisplay = controls ? controls.style.display : '';
  if (controls) {
    controls.style.display = 'none';
  }

  // 临时展开滚动容器。座位图实际内容可能大于当前管理端视口，必须让 DOM 盒子按完整内容参与截图。
  element.style.overflow = 'visible';
  element.style.maxHeight = 'none';
  element.scrollLeft = 0;
  element.scrollTop = 0;

  if (viewport) {
    viewport.style.overflow = 'visible';
    viewport.style.maxHeight = 'none';
  }

  const exportBox = resolveExportBox(element);
  element.style.width = `${exportBox.width}px`;
  element.style.height = `${exportBox.height}px`;
  element.style.minWidth = `${exportBox.width}px`;
  element.style.minHeight = `${exportBox.height}px`;

  // 返回清理函数，恢复原始状态
  return () => {
    restoreElementStyles();
    restoreViewportStyles?.();
    if (controls) {
      controls.style.display = originalControlsDisplay;
    }
    element.scrollLeft = originalScrollLeft;
    element.scrollTop = originalScrollTop;
  };
}

/**
 * 导出座位表的便捷方法
 * 
 * @param boardElement 座位图元素
 * @param format 导出格式
 * @param meetingName 会议名称（用于文件名）
 */
export async function exportSeating(
  boardElement: HTMLElement | null,
  format: ExportFormat,
  meetingName?: string
): Promise<void> {
  if (!boardElement) {
    throw new Error('未找到座位图元素');
  }

  // 准备导出
  const cleanup = prepareExportElement(boardElement);

  try {
    // 生成文件名
    const timestamp = new Date().toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    }).replace(/\//g, '-').replace(/:/g, '-').replace(/\s/g, '_');
    
    const filename = meetingName 
      ? `${meetingName}_座位表_${timestamp}`
      : `座位表_${timestamp}`;

    // 执行导出
    await exportSeatingChart(boardElement, {
      format,
      filename,
      quality: 0.95,
      orientation: 'landscape',
    });
  } finally {
    // 清理，恢复原始状态
    cleanup();
  }
}
