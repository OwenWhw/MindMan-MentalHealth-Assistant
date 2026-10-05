<script setup>
defineProps({
  color: { type: String, default: '#059669' },
  size:  { type: [Number, String], default: 24 }
})
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 48 48"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
  >
    <!-- 五片花瓣 -->
    <path d="M24 4 C26 12 30 16 38 18 C30 22 26 26 24 34 C22 26 18 22 10 18 C18 16 22 12 24 4Z"
      :fill="color" opacity="0.85" />
    <path d="M44 18 C40 24 38 30 38 38 C32 34 26 32 20 34 C26 28 28 22 44 18Z"
      :fill="color" opacity="0.75" transform="rotate(0 24 24)" />
    <path d="M44 18 C40 24 38 30 38 38 C32 34 26 32 20 34 C26 28 28 22 44 18Z"
      :fill="color" opacity="0.75" transform="rotate(72 24 24)" />
    <path d="M44 18 C40 24 38 30 38 38 C32 34 26 32 20 34 C26 28 28 22 44 18Z"
      :fill="color" opacity="0.75" transform="rotate(144 24 24)" />
    <path d="M44 18 C40 24 38 30 38 38 C32 34 26 32 20 34 C26 28 28 22 44 18Z"
      :fill="color" opacity="0.75" transform="rotate(216 24 24)" />
    <path d="M44 18 C40 24 38 30 38 38 C32 34 26 32 20 34 C26 28 28 22 44 18Z"
      :fill="color" opacity="0.75" transform="rotate(288 24 24)" />
    <!-- 花蕊 -->
    <circle cx="24" cy="24" r="7"
      :fill="color" opacity="0.4" />
    <circle cx="24" cy="24" r="4"
      fill="#facc15" />
    <!-- 茎 -->
    <line x1="24" y1="34" x2="24" y2="46"
      :stroke="color" stroke-width="2.5" stroke-linecap="round" />
    <!-- 叶 (左) -->
    <path d="M24 40 Q16 36 12 42"
      :stroke="color" stroke-width="2" stroke-linecap="round" fill="none" />
    <!-- 叶 (右) -->
    <path d="M24 38 Q32 34 36 40"
      :stroke="color" stroke-width="2" stroke-linecap="round" fill="none" />
  </svg>
</template>
