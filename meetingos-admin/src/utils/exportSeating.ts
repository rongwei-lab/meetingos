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

    // 使用 html2canvas 将 DOM 转为 canvas
    // 配置参数优化导出质量
    const canvas = await html2canvas(element, {
      backgroundColor: '#ffffff',
      scale: 2, // 提高分辨率，2倍清晰度
      useCORS: true, // 支持跨域图片
      logging: false,
      windowWidth: element.scrollWidth,
      windowHeight: element.scrollHeight,
    });

    if (format === 'pdf') {
      // 导出为 PDF
      await exportToPDF(canvas, filename, orientation);
    } else {
      // 导出为图片（PNG 或 JPEG）
      exportToImage(canvas, filename, format, quality);
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
): void {
  const mimeType = format === 'png' ? 'image/png' : 'image/jpeg';
  const extension = format === 'png' ? 'png' : 'jpg';

  // 将 canvas 转为 blob
  canvas.toBlob(
    (blob) => {
      if (!blob) {
        throw new Error('Canvas 转换失败');
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
    },
    mimeType,
    quality
  );
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
  const imgWidth = canvas.width;
  const imgHeight = canvas.height;

  // 计算 PDF 尺寸（A4 纸张，单位 mm）
  const pdfWidth = orientation === 'portrait' ? 210 : 297;
  const pdfHeight = orientation === 'portrait' ? 297 : 210;

  // 计算图片在 PDF 中的尺寸，保持宽高比
  const ratio = Math.min(pdfWidth / imgWidth, pdfHeight / imgHeight);
  const imgPdfWidth = imgWidth * ratio;
  const imgPdfHeight = imgHeight * ratio;

  // 居中放置
  const marginX = (pdfWidth - imgPdfWidth) / 2;
  const marginY = (pdfHeight - imgPdfHeight) / 2;

  // 创建 PDF 文档
  const pdf = new jsPDF({
    orientation,
    unit: 'mm',
    format: 'a4',
  });

  // 将 canvas 转为图片数据
  const imgData = canvas.toDataURL('image/jpeg', 0.95);

  // 添加图片到 PDF
  pdf.addImage(imgData, 'JPEG', marginX, marginY, imgPdfWidth, imgPdfHeight);

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
  // 保存原始样式
  const originalOverflow = element.style.overflow;
  const originalMaxHeight = element.style.maxHeight;

  // 临时修改样式，确保完整渲染
  element.style.overflow = 'visible';
  element.style.maxHeight = 'none';

  // 隐藏控制按钮（缩放控件等）
  const controls = element.querySelector('.seat-board-controls') as HTMLElement;
  const originalControlsDisplay = controls ? controls.style.display : '';
  if (controls) {
    controls.style.display = 'none';
  }

  // 返回清理函数，恢复原始状态
  return () => {
    element.style.overflow = originalOverflow;
    element.style.maxHeight = originalMaxHeight;
    if (controls) {
      controls.style.display = originalControlsDisplay;
    }
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
