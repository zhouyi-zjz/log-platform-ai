<script setup lang="ts">
/**
 * 用户头像组件
 * - 传入非空 avatar 时展示真实头像图片
 * - 传入空字符串时，或图片加载失败时，根据用户名哈希生成专属渐变色 + 姓名首字母
 * @prop avatar   由父组件传入、已处理的头像 URL，无自定义头像时请传空字符串
 */
import { computed, ref, watch } from 'vue';

const props = withDefaults(defineProps<{
  avatar?: string;
  nickname?: string;
  username?: string;
  size?: number;
}>(), {
  avatar: '',
  nickname: '',
  username: '',
  size: 32
});

// 12 组精心挑选的渐变配色
const GRADIENT_PALETTES = [
  ['#667EEA', '#764BA2'],  // 蓝紫
  ['#F093FB', '#F5576C'],  // 粉红
  ['#4FACFE', '#00C9FF'],  // 天蓝
  ['#43E97B', '#38F9D7'],  // 翠绿
  ['#FA709A', '#FE9A8B'],  // 珊瑚粉
  ['#4ECDC4', '#2BC0B4'],  // 青绿
  ['#FF6B6B', '#FF8E53'],  // 橘红
  ['#6C5CE7', '#A29BFE'],  // 深紫
  ['#FD7B52', '#F5AF19'],  // 橙黄
  ['#11998E', '#38EF7D'],  // 森绿
  ['#FC5C7D', '#6A3093'],  // 玫瑞紫
  ['#3B4371', '#F3904F'],  // 靖橙
];

/** 将字符串哈希映射到 0~n-1 的整数，保证同一用户颜色稳定 */
const hashString = (str: string): number => {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = (hash * 31 + str.charCodeAt(i)) >>> 0;
  }
  return hash;
};

// 图片加载失败标记
const imgError = ref(false);

// avatar prop 变化时重置错误状态，以便重新尝试加载新图片
watch(() => props.avatar, () => { imgError.value = false; });

/**
 * 是否展示真实头像：
 * - 父组件传入空字符串时表示无自定义头像，展示生成头像
 * - 图片加载失败后降级展示生成头像
 * 注意：不再依赖 defAva 字符串比较，避免 Vite 多模块上下文返回不同 URL 导致判断失败
 */
const showRealAvatar = computed((): boolean => {
  return !!props.avatar && !imgError.value;
});

/** 图片加载失败回调 */
const handleImgError = (): void => {
  imgError.value = true;
};

/** 根据昵称/用户名哈希值选取渐变色 */
const gradient = computed((): string => {
  const seed = props.nickname || props.username || 'user';
  const idx = hashString(seed) % GRADIENT_PALETTES.length;
  const [from, to] = GRADIENT_PALETTES[idx];
  return `linear-gradient(135deg, ${from} 0%, ${to} 100%)`;
});

/** 显示文字：中文取前 2 字，英文取首字母缩写（最多 2 个） */
const initials = computed((): string => {
  const name = (props.nickname || props.username || 'U').trim();
  // 中文名：取前两个字符（单字名取 1 个）
  if (/^[\u4e00-\u9fa5]/.test(name)) {
    return name.slice(0, 2);
  }
  // 英文名：取每个单词首字母，最多 2 个
  const words = name.split(/\s+/).filter(Boolean);
  if (words.length === 1) return words[0].charAt(0).toUpperCase();
  return (words[0].charAt(0) + words[words.length - 1].charAt(0)).toUpperCase();
});

/** 根据字符数动态调整字号 */
const fontSize = computed((): string => {
  const isTwoChar = initials.value.length >= 2;
  const base = props.size * (isTwoChar ? 0.36 : 0.44);
  return `${Math.round(base)}px`;
});

/** 根元素样式：单一根节点避免 Vue3 片段导致的 attr 继承异常 */
const rootStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  borderRadius: '8px',
  flexShrink: '0',
  overflow: 'hidden',
  background: showRealAvatar.value ? 'transparent' : gradient.value,
  fontSize: showRealAvatar.value ? undefined : fontSize.value,
}));
</script>

<template>
  <!--
    单一根节点 div，避免 Vue3 片段问题导致的 @error 事件失联。
    父组件不传入 avatar （或传空字符串）时直接展示生成头像。
  -->
  <div class="user-avatar" :style="rootStyle">
    <!-- 真实头像图片 -->
    <img
      v-if="showRealAvatar"
      :src="avatar"
      alt="用户头像"
      class="user-avatar__img"
      @error="handleImgError"
    />
    <!-- 生成头像：渐变色圆角方形 + 姓名首字母 -->
    <span v-else class="user-avatar__text">{{ initials }}</span>
  </div>
</template>

<style scoped>
.user-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: inset 0 1px 3px rgba(255, 255, 255, 0.25), 0 2px 8px rgba(0, 0, 0, 0.15);
}

.user-avatar__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.user-avatar__text {
  color: #ffffff;
  font-weight: 600;
  letter-spacing: 0.5px;
  user-select: none;
  line-height: 1;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
}
</style>
