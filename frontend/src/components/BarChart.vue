<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  data: { type: Array, default: () => [] }
})

const el = ref(null)
let chart = null

function option() {
  // 横向柱状图：从小到大排，最大的自然落在最上面
  const rows = [...(props.data || [])].sort((a, b) => Number(a.value) - Number(b.value))

  return {
    grid: { left: 4, right: 40, top: 8, bottom: 4, containLabel: true },
    xAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#1c1e22' } },
      axisLabel: { color: '#62666d', fontSize: 11 }
    },
    yAxis: {
      type: 'category',
      data: rows.map((r) => r.name),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#8a8f98', fontSize: 12 }
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(94,106,210,0.08)' } },
      backgroundColor: '#141516',
      borderColor: '#23252a',
      borderWidth: 1,
      padding: [8, 10],
      textStyle: { color: '#f7f8f8', fontSize: 12 },
      extraCssText: 'border-radius:8px;box-shadow:none;',
      formatter: (ps) => `${ps[0].name}<br/>${ps[0].value} 次`
    },
    series: [
      {
        type: 'bar',
        data: rows.map((r) => Number(r.value || 0)),
        barWidth: 14,
        itemStyle: {
          borderRadius: [0, 4, 4, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#3b4178' },
            { offset: 1, color: '#828fff' }
          ])
        },
        label: { show: true, position: 'right', color: '#8a8f98', fontSize: 11 }
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
