<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  data: { type: Array, default: () => [] }
})

// Linear 的调色板：强调色打头，其余是中性阶梯，最多一个辅助绿
const PALETTE = ['#5e6ad2', '#828fff', '#4a55b8', '#27a644', '#f5a524', '#e5484d', '#8a8f98', '#62666d']

const el = ref(null)
let chart = null

function option() {
  return {
    color: PALETTE,
    tooltip: {
      trigger: 'item',
      backgroundColor: '#141516',
      borderColor: '#23252a',
      borderWidth: 1,
      padding: [8, 10],
      textStyle: { color: '#f7f8f8', fontSize: 12 },
      extraCssText: 'border-radius:8px;box-shadow:none;',
      formatter: (p) => `${p.name}<br/>${p.value} 次（${p.percent}%）`
    },
    legend: {
      orient: 'vertical',
      right: 0,
      top: 'middle',
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      itemGap: 10,
      textStyle: { color: '#8a8f98', fontSize: 12 },
      formatter: (name) => (name.length > 14 ? `${name.slice(0, 13)}…` : name)
    },
    series: [
      {
        type: 'pie',
        radius: ['56%', '80%'],
        center: ['30%', '50%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#0f1011', borderWidth: 2 },
        label: { show: false },
        labelLine: { show: false },
        emphasis: { scale: true, scaleSize: 5 },
        data: (props.data || []).map((d) => ({ name: d.name, value: Number(d.value || 0) }))
      }
    ]
  }
}

function render() {
  if (chart) chart.setOption(option(), true)
}

function resize() {
  chart?.resize()
}

onMounted(() => {
  chart = echarts.init(el.value)
  render()
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
  chart = null
})

watch(() => props.data, render, { deep: true })
</script>

<template>
  <div ref="el" class="chart" />
</template>
