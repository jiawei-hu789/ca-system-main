<script setup>
import { computed } from 'vue'
import { ElTag } from 'element-plus'
import {
  applicationStatus,
  certificateStatus,
  revocationStatus,
  revocationCheck,
  roleLabel,
} from '../utils/format'

const props = defineProps({
  status: { type: String, default: '' },
  /** application | certificate | revocation | revocationCheck | role */
  kind: { type: String, default: 'application' },
  size: { type: String, default: 'default' },
})

const RESOLVERS = {
  application: applicationStatus,
  certificate: certificateStatus,
  revocation: revocationStatus,
  revocationCheck,
  role: roleLabel,
}

const view = computed(() => {
  const resolver = RESOLVERS[props.kind] || applicationStatus
  return resolver(props.status)
})
</script>

<template>
  <ElTag :type="view.type" :size="size" effect="light" round>{{ view.label }}</ElTag>
</template>
