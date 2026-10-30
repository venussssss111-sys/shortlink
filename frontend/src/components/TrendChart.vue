<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  /** [{ statDate: '2026-10-27', pv: 10, uv: 1 }, ...] */
  data: { type: Array, default: () => [] }
})

const el = ref(null)
let chart = null

// Linear 调色板：唯一彩色是薰衣草蓝，UV 用中性灰虚线，不引入第二个彩色
const INK = '#f7f8f8'
const SUBTLE = '#8a8f98'
const HAIRLINE = '#23252a'
const GRID = '#18191a'
const PRIMARY = '#5e6ad2'
const NEUTRAL = '#62666d'

function render() {
  if (!chart) return
  const rows = props.data || []
  const dates = rows.map((d) => d.statDate)
  const pv = rows.map((d) => Number(d.pv || 0))
  const uv = rows.map((d) => Number(d.uv || 0))

  chart.setOption(
    {
      backgroundColor: 'transparent',
      animationDuration: 320,
      grid: { left: 4, right: 28, top: 16, bottom: 20, containLabel: true },
      tooltip: {
        trigger: 'axis',
        backgroundColor: '#141516',
        borderColor: HAIRLINE,
        borderWidth: 1,
        padding: [8, 12],
        textStyle: { color: INK, fontSize: 12 },
        axisPointer: { type: 'line', lineStyle: { color: HAIRLINE } }
      },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: dates,
        axisLine: { lineStyle: { color: HAIRLINE } },
        axisTick: { show: false },
        axisLabel: { color: SUBTLE, fontSize: 11, formatter: (v) => String(v).slice(5) }
      },
      yAxis: {
        type: 'value',
        minInterval: 1,
        axisLine: { show: false },
        axisTick: { show: false },
        splitLine: { lineStyle: { color: GRID } },
        axisLabel: { color: SUBTLE, fontSize: 11 }
      },
      series: [
        {
          name: 'PV',
          type: 'line',
          smooth: 0.35,
          showSymbol: false,
          symbolSize: 6,
          data: pv,
          lineStyle: { color: PRIMARY, width: 2 },
          itemStyle: { color: PRIMARY },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(94, 106, 210, 0.30)' },
              { offset: 1, color: 'rgba(94, 106, 210, 0)' }
            ])
          }
        },
        {
          name: 'UV',
          type: 'line',
          smooth: 0.35,
          showSymbol: false,
          data: uv,
          lineStyle: { color: NEUTRAL, width: 1.5, type: 'dashed' },
          itemStyle: { color: NEUTRAL }
        }
      ]
    },
    true
  )
}

function resize() {
  if (chart) chart.resize()
}

onMounted(() => {
  chart = echarts.init(el.value)
  render()
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  if (chart) chart.dispose()
  chart = null
})

watch(() => props.data, render, { deep: true })
</script>

<template>
  <div ref="el" class="chart" />
</template>

<style scoped>
.chart {
  width: 100%;
  height: 100%;
  min-height: 240px;
}
</style>
