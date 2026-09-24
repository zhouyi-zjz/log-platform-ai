/**
 * IM 聊天 UI 工具（私信 / 群聊共用）
 */

/** 消息时间分隔阈值（分钟），参考微信 */
export const CHAT_TIME_GAP_MINUTES = 5;

export interface ChatTimeDividerItem {
  kind: 'time';
  key: string;
  label: string;
}

export interface ChatMessageItem<T> {
  kind: 'message';
  key: string;
  msg: T;
}

export type ChatListItem<T> = ChatTimeDividerItem | ChatMessageItem<T>;

interface HasCreateTime {
  id?: number;
  clientMsgId?: string;
  createTime?: string;
}

/** 解析 RuoYi 返回的时间字符串 */
export function parseChatTime(time?: string): number {
  if (!time) return NaN;
  return new Date(time.replace(/-/g, '/')).getTime();
}

/**
 * 格式化聊天时间分隔条文案（参考微信）
 * - 今天：10:30
 * - 昨天：昨天 10:30
 * - 今年：6月10日 10:30
 * - 更早：2025年6月10日 10:30
 */
export function formatChatTimeDivider(time?: string): string {
  const ts = parseChatTime(time);
  if (Number.isNaN(ts)) return time || '';

  const date = new Date(ts);
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  const hm = `${pad(date.getHours())}:${pad(date.getMinutes())}`;

  const isSameDay = (a: Date, b: Date) =>
    a.getFullYear() === b.getFullYear()
    && a.getMonth() === b.getMonth()
    && a.getDate() === b.getDate();

  if (isSameDay(date, now)) return hm;

  const yesterday = new Date(now);
  yesterday.setDate(yesterday.getDate() - 1);
  if (isSameDay(date, yesterday)) return `昨天 ${hm}`;

  if (date.getFullYear() === now.getFullYear()) {
    return `${date.getMonth() + 1}月${date.getDate()}日 ${hm}`;
  }

  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日 ${hm}`;
}

/**
 * 将消息列表插入时间分隔条
 * @param messages 按时间升序的消息数组
 * @param gapMinutes 相邻消息超过该分钟数则插入分隔条
 */
export function buildChatListWithTimeDividers<T extends HasCreateTime>(
  messages: T[],
  gapMinutes = CHAT_TIME_GAP_MINUTES
): ChatListItem<T>[] {
  const gapMs = gapMinutes * 60 * 1000;
  const items: ChatListItem<T>[] = [];
  let lastTs = 0;

  messages.forEach((msg, index) => {
    const ts = parseChatTime(msg.createTime);
    const shouldShowTime =
      index === 0
      || Number.isNaN(ts)
      || Number.isNaN(lastTs)
      || ts - lastTs >= gapMs;

    if (shouldShowTime && msg.createTime) {
      items.push({
        kind: 'time',
        key: `time-${msg.id || msg.clientMsgId || index}`,
        label: formatChatTimeDivider(msg.createTime)
      });
    }

    items.push({
      kind: 'message',
      key: String(msg.id || msg.clientMsgId || `msg-${index}`),
      msg
    });

    if (!Number.isNaN(ts)) lastTs = ts;
  });

  return items;
}
