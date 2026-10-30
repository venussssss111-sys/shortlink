<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { aggregate, getDevices, getLinkStats, getOverview, getSources, getTopLinks } from '../api/stats'
import { shortUrlOf } from '../api/link'
import TrendChart from '../components/TrendChart.vue'
import PieChart from '../components/PieChart.vue'
import BarChart from '../components/BarChart.vue'

const days = ref(7)
const overview = ref({ linkCount: 0, todayPv: 0, todayUv: 0, totalPv: 0 })
const topLinks = ref([])
const sources = ref([])
const devices = ref([])
const selected = ref('')
const detail = ref(null)
const codeInput = ref('')
const loadingTop = ref(false)
const loadingDetail = ref(false)

const maxPv = computed(() => {
  const nums = topLinks.value.map((r) => Number(r.pv || 0))
  return nums.length ? Math.max(...nums) || 1 : 1
})

const fmt = (n) => Number(n || 0).toLocaleString('zh-CN')

const pad2 = (n) => String(n).padStart(2, '0')

/**
 * 后端只返回「有数据的天」，直接丢给图表会出现「只有一天 → 画不出线」。
 * 这里把整个窗口补齐成 0，图才是完整的 7/14/30 天。
 */
const chartData = computed(() => {
  const map = new Map((detail.value?.trend || []).map((r) => [r.statDate, r]))
  const today = new Date()
  const out = []
  for (let i = days.value - 1; i >= 0; i -= 1) {
    const d = new Date(today)
    d.setDate(today.getDate() - i)
    const key = `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
    const row = map.get(key)
    out.push({ statDate: key, pv: Number(row?.pv || 0), uv: Number(row?.uv || 0) })
  }
  return out
})

async function loadOverview() {
  try {
    overview.value = await getOverview()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function loadTop() {
  loadingTop.value = true
  try {
    topLinks.value = (await getTopLinks(days.value, 8)) || []
    if (!selected.value && topLinks.value.length) {
      selected.value = topLinks.value[0].code
    }
  } catch (e) {
    topLinks.value = []
  } finally {
    loadingTop.value = false
  }
}

async function loadSources() {
  try {
    sources.value = (await getSources(days.value)) || []
  } catch (e) {
    sources.value = []
  }
}

async function loadDevices() {
  try {
    devices.value = (await getDevices(days.value)) || []
  } catch (e) {
    devices.value = []
  }
}

async function loadDetail() {
  if (!selected.value) {
    detail.value = null
    return
  }
  loadingDetail.value = true
  try {
    detail.value = await getLinkStats(selected.value, days.value)
  } catch (e) {
    detail.value = null
  } finally {
    loadingDetail.value = false
  }
}

async function refreshAll() {
  await Promise.all([loadOverview(), loadTop(), loadSources(), loadDevices()])
  await loadDetail()
}

function pick(code) {
  selected.value = code
  loadDetail()
}

function search() {
  const code = codeInput.value.trim()
  if (!code) return
  selected.value = code
  loadDetail()
}

async function doAggregate() {
  try {
    const n = await aggregate()
    ElMessage.success(n > 0 ? `已聚合 ${n} 条记录` : '今天还没有访问记录')
    await refreshAll()
  } catch (e) {
    /* 已提示 */
  }
}

function setDays(d) {
  if (days.value === d) return
  days.value = d
  refreshAll()
}

onMounted(refreshAll)
</script>

<template>
  <div class="dash">
    <header class="dash-head">
      <div>
        <div class="eyebrow">Analytics</div>
        <h1 class="display">访问统计</h1>
        <p class="sub">
          今天的数在 Redis 里，历史在 MySQL 里；这里把两处合起来看。点排行榜里的短码可以切换趋势。
        </p>
      </div>
      <div class="head-actions">
        <button class="btn ghost" @click="doAggregate">聚合今天</button>
        <button class="btn primary" @click="refreshAll">刷新</button>
      </div>
    </header>

    <section class="kpis">
      <div class="card kpi">
        <div class="cap">短链总数</div>
        <div class="num">{{ fmt(overview.linkCount) }}</div>
      </div>
      <div class="card kpi">
        <div class="cap">今日 PV</div>
        <div class="num accent">{{ fmt(overview.todayPv) }}</div>
      </div>
      <div class="card kpi">
        <div class="cap">今日 UV</div>
        <div class="num">{{ fmt(overview.todayUv) }}</div>
      </div>
      <div class="card kpi">
        <div class="cap">累计 PV</div>
        <div class="num">{{ fmt(overview.totalPv) }}</div>
      </div>
    </section>

    <section class="grid2">
      <div class="card">
        <div class="card-head">
          <div>
            <div class="cap">趋势 · 近 {{ days }} 天</div>
            <div class="card-title" style="margin-top: 6px">
              {{ detail?.code || '未选择短码' }}
            </div>
            <div v-if="detail" class="mono muted" style="margin-top: 4px">
              {{ shortUrlOf(detail.code) }}
            </div>
          </div>
          <div class="pills">
            <button
              v-for="d in [7, 14, 30]"
              :key="d"
              :class="['pill', { on: days === d }]"
              @click="setDays(d)"
            >
              {{ d }} 天
            </button>
          </div>
        </div>

        <div class="chart-wrap">
          <TrendChart :data="chartData" />
        </div>

        <div class="legend">
          <span class="dot pv" />PV
          <b>{{ fmt(detail?.recentPv) }}</b>
          <span class="dot uv" style="margin-left: 12px" />UV
          <b>{{ fmt(detail?.recentUv) }}</b>
          <span class="grow" />
          <span class="muted mono">
            今日 {{ fmt(detail?.todayPv) }} · 累计 {{ fmt(detail?.totalPv) }}
          </span>
        </div>
      </div>

      <div class="card">
        <div class="card-head">
          <div class="cap">Top 短链 · 近 {{ days }} 天</div>
          <span class="muted mono">{{ fmt(topLinks.length) }} 条</span>
        </div>

        <ul class="rank">
          <li
            v-for="(r, i) in topLinks"
            :key="r.code"
            :class="['rank-row', { on: r.code === selected }]"
            @click="pick(r.code)"
          >
            <span class="idx mono">{{ String(i + 1).padStart(2, '0') }}</span>
            <span class="code mono">{{ r.code }}</span>
            <span class="bar"><i :style="{ width: (Number(r.pv) / maxPv) * 100 + '%' }" /></span>
            <span class="pv mono">{{ fmt(r.pv) }}</span>
          </li>
          <li v-if="!topLinks.length && !loadingTop" class="empty">
            还没有统计数据。先点几次短链，再点右上角「聚合今天」。
          </li>
        </ul>
      </div>
    </section>

    <section class="grid2">
      <div class="card">
        <div class="card-head">
          <div>
            <div class="cap">来源 · 近 {{ days }} 天</div>
            <div class="card-title" style="margin-top: 6px">访问者从哪来</div>
          </div>
          <span class="muted mono">{{ sources.length }} 个来源</span>
        </div>
        <div class="chart-wrap sm">
          <PieChart v-if="sources.length" :data="sources" />
          <div v-else class="lookup-empty">还没有 referer 数据。从别的页面点开短链才会记下来源。</div>
        </div>
      </div>

      <div class="card">
        <div class="card-head">
          <div>
            <div class="cap">设备 · 近 {{ days }} 天</div>
            <div class="card-title" style="margin-top: 6px">用什么设备点</div>
          </div>
          <span class="muted mono">按 UA 归类</span>
        </div>
        <div class="chart-wrap sm">
          <BarChart v-if="devices.length" :data="devices" />
          <div v-else class="lookup-empty">还没有访问数据。</div>
        </div>
      </div>
    </section>

    <section class="card lookup">
      <div class="cap" style="margin-bottom: 10px">查单条</div>
      <div class="lookup-bar">
        <input
          v-model="codeInput"
          class="input mono"
          placeholder="输入短码，如 V0002"
          @keyup.enter="search"
        />
        <button class="btn ghost" @click="search">查询</button>
      </div>

      <div v-if="detail" class="lookup-body">
        <div class="mini-stat">
          <div class="cap">今日 PV</div>
          <div class="v">{{ fmt(detail.todayPv) }}</div>
        </div>
        <div class="mini-stat">
          <div class="cap">今日 UV</div>
          <div class="v">{{ fmt(detail.todayUv) }}</div>
        </div>
        <div class="mini-stat">
          <div class="cap">近 {{ detail.recentDays }} 天 PV</div>
          <div class="v">{{ fmt(detail.recentPv) }}</div>
        </div>
        <div class="mini-stat">
          <div class="cap">累计 PV</div>
          <div class="v">{{ fmt(detail.totalPv) }}</div>
        </div>
      </div>
      <div v-else class="lookup-empty">
        输入一个短码查询它的全部统计（会同时查 MySQL 的历史和 Redis 的今天）。
      </div>
    </section>
  </div>
</template>
