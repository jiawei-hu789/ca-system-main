<script setup>
import { computed } from 'vue'
import { ElButton } from 'element-plus'

/**
 * 后端列表接口只返回 limit/offset，不返回总条数，
 * 因此这里只能提供「上一页 / 下一页」，不能用 el-pagination 算总页数。
 */
const props = defineProps({
  offset: { type: Number, required: true },
  limit: { type: Number, required: true },
  /** 本次返回的条目数，用于判断是否还有下一页 */
  count: { type: Number, required: true },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: null },
})

const emit = defineEmits(['change'])

const page = computed(() => Math.floor(props.offset / props.limit) + 1)
const hasPrev = computed(() => props.offset > 0)
const hasNext = computed(() => props.count >= props.limit)

function go(delta) {
  const next = props.offset + delta * props.limit
  emit('change', Math.max(0, next))
}
</script>

<template>
  <div class="list-pager">
    <span class="list-pager__info">
      第 {{ page }} 页 · 本页 {{ count }} 条<template v-if="total !== null"> · 共 {{ total }} 条</template>
    </span>
    <div class="list-pager__actions">
      <ElButton :disabled="!hasPrev || loading" :loading="loading" @click="go(-1)">上一页</ElButton>
      <ElButton :disabled="!hasNext || loading" :loading="loading" @click="go(1)">下一页</ElButton>
    </div>
  </div>
</template>

<style scoped>
.list-pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-top: 18px;
}
.list-pager__info { color: var(--muted); font-size: 12px; }
.list-pager__actions { display: flex; gap: 8px; }
</style>
