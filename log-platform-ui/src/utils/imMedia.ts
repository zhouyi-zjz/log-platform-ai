/**
 * IM 图片/文件消息解析工具
 */

export interface MediaPayload {
  url: string;
  name?: string;
  size?: number;
}

/** 将相对路径转为可访问的完整 URL */
export function resolveMediaUrl(url: string): string {
  if (!url) return '';
  if (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('blob:')) {
    return url;
  }
  const base = import.meta.env.VITE_APP_BASE_API || '';
  return `${base}${url.startsWith('/') ? url : `/${url}`}`;
}

/** 解析 IMAGE/FILE 消息的 content（支持 JSON 或纯 URL） */
export function parseMediaContent(content: string, type?: string): MediaPayload | null {
  if (!content || (type !== 'IMAGE' && type !== 'FILE')) return null;
  try {
    const parsed = JSON.parse(content);
    if (parsed?.url) return parsed as MediaPayload;
  } catch {
    // 纯 URL 字符串
  }
  return { url: content, name: content.split('/').pop() || '文件' };
}

/** 人类可读的文件大小 */
export function formatFileSize(bytes?: number): string {
  if (!bytes || bytes <= 0) return '';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** 撤回时间窗口：2 分钟 */
export const RECALL_WINDOW_MS = 2 * 60 * 1000;

/** 判断消息是否仍在可撤回时间内 */
export function canRecallByTime(createTime?: string): boolean {
  if (!createTime) return false;
  const ts = new Date(createTime.replace(/-/g, '/')).getTime();
  if (Number.isNaN(ts)) return false;
  return Date.now() - ts <= RECALL_WINDOW_MS;
}
