import type MarkdownIt from 'markdown-it';
import type { RenderRule } from 'markdown-it/lib/renderer';
import { ElMessage } from 'element-plus';

/** HTML 内容缓存 storageId → html */
const htmlCache: Record<string, string> = {};

/** 消息原文缓存 messageKey → content（缓存 miss 时回退解析） */
const messageContentCache: Record<string, string> = {};

export interface TravelHtmlPreviewState {
  visible: boolean;
  title: string;
  content: string;
}

const TRAVEL_HTML_BLOCK_RE = /```(?:travel-html|html)\s*\n([\s\S]*?)```/i;

export function cacheTravelHtml(storageId: string, html: string): void {
  htmlCache[storageId] = html;
}

export function cacheMessageContent(messageKey: string, content: string): void {
  messageContentCache[messageKey] = content;
}

export function extractTravelHtml(content: string): string | null {
  if (!content) return null;
  const match = content.match(TRAVEL_HTML_BLOCK_RE);
  return match ? match[1].trim() : null;
}

export function stripTravelHtmlBlock(content: string): string {
  return content.replace(/```(?:travel-html|html)\s*\n[\s\S]*?```/gi, '').trim();
}

export function resolveTravelHtml(storageId: string, messageKey?: string): string | null {
  const cached = htmlCache[storageId];
  if (cached) return cached;
  if (messageKey && messageContentCache[messageKey]) {
    return extractTravelHtml(messageContentCache[messageKey]);
  }
  return null;
}

export function downloadTravelHtml(html: string, filename?: string): void {
  const blob = new Blob([html], { type: 'text/html;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename || `travel-plan-${Date.now()}.html`;
  a.style.display = 'none';
  document.body.appendChild(a);
  a.click();
  setTimeout(() => {
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }, 100);
}

export function openTravelHtmlInNewWindow(html: string): void {
  const blob = new Blob([html], { type: 'text/html;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  window.open(url, '_blank');
  setTimeout(() => URL.revokeObjectURL(url), 60000);
}

/** 是否为完整 HTML 文档（非代码块包裹的裸 HTML） */
export function isRawHtmlDocument(content: string): boolean {
  if (!content) return false;
  const t = content.trim();
  return /^<!DOCTYPE\s+html/i.test(t)
    || /^<html[\s>]/i.test(t)
    || (t.startsWith('<') && /<\/html>/i.test(t));
}

function buildPreviewToolbarHtml(
  storageId: string,
  messageKey: string,
  label = '🗺️ 旅游行程报告'
): string {
  return `
    <div class="travel-html-preview-wrapper" data-storage-id="${storageId}" data-message-key="${messageKey}">
      <div class="travel-html-preview-toolbar">
        <span class="travel-html-preview-label">${label}</span>
        <div class="travel-html-preview-actions">
          <button type="button" class="travel-html-btn" data-travel-action="preview" data-storage-id="${storageId}" data-message-key="${messageKey}">查看报告</button>
          <button type="button" class="travel-html-btn" data-travel-action="download" data-storage-id="${storageId}" data-message-key="${messageKey}">下载 HTML</button>
          <button type="button" class="travel-html-btn" data-travel-action="open" data-storage-id="${storageId}" data-message-key="${messageKey}">新窗口打开</button>
        </div>
      </div>
    </div>
  `;
}

/** 为 MarkdownIt 注册 travel-html 代码块渲染规则 */
export function setupTravelHtmlFenceRule(markdownIt: MarkdownIt): void {
  const defaultFence: RenderRule | undefined = markdownIt.renderer.rules.fence;
  markdownIt.renderer.rules.fence = (tokens, idx, options, env, self) => {
    const token = tokens[idx];
    const language = token.info.trim();
    const codeContent = token.content;

    const isHtmlDoc = codeContent.includes('<!DOCTYPE') || codeContent.includes('<html');
    if (
      language === 'travel-html'
      || language === 'html'
      || (!language && isHtmlDoc)
      || (language === '' && isHtmlDoc)
    ) {
      const messageKey = String(env?.messageKey ?? `msg-${idx}`);
      const storageId = `${messageKey}-block-${idx}`;
      cacheTravelHtml(storageId, codeContent.trim());
      const label = language === 'travel-html' ? '🗺️ 旅游行程报告' : '📄 HTML 页面';
      return buildPreviewToolbarHtml(storageId, messageKey, label);
    }

    return defaultFence!(tokens, idx, options, env, self);
  };
}

/** 渲染 markdown，同时缓存消息原文供预览回退 */
export function renderMarkdownWithTravelHtml(
  markdownIt: MarkdownIt,
  content: string,
  messageKey: string | number
): string {
  const key = String(messageKey);
  cacheMessageContent(key, content);
  return markdownIt.render(content, { messageKey: key });
}

/**
 * 智能体消息渲染：Markdown + HTML 代码块/裸 HTML 文档 → 可预览页面（非原始代码）
 */
export function renderAgentMessageContent(
  markdownIt: MarkdownIt,
  content: string,
  messageKey: string | number
): string {
  const key = String(messageKey);
  if (isRawHtmlDocument(content)) {
    const storageId = `${key}-raw-html`;
    cacheTravelHtml(storageId, content.trim());
    cacheMessageContent(key, content);
    return buildPreviewToolbarHtml(storageId, key, '📄 智能体页面');
  }
  return renderMarkdownWithTravelHtml(markdownIt, content, key);
}

/** 文档级点击委托（需在 onMounted 注册） */
export function createTravelHtmlClickHandler(previewState: TravelHtmlPreviewState) {
  return (event: Event): void => {
    const target = event.target as HTMLElement;
    const btn = target.closest('[data-travel-action]') as HTMLElement | null;
    if (!btn) return;

    event.preventDefault();
    event.stopPropagation();

    const action = btn.dataset.travelAction;
    const storageId = btn.dataset.storageId || '';
    const messageKey = btn.dataset.messageKey;
    const html = resolveTravelHtml(storageId, messageKey);

    if (!html) {
      ElMessage.warning('报告内容未找到，请刷新后重试');
      return;
    }

    if (action === 'preview') {
      previewState.content = html;
      previewState.visible = true;
    } else if (action === 'download') {
      downloadTravelHtml(html);
      ElMessage.success('报告已开始下载');
    } else if (action === 'open') {
      openTravelHtmlInNewWindow(html);
    }
  };
}
