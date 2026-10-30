import http from './http'

/** 全局总览：GET /api/stats/overview */
export const getOverview = () => http.get('/stats/overview')

/** 排行榜：GET /api/stats/top?days=7&limit=8 */
export const getTopLinks = (days = 7, limit = 8) =>
  http.get('/stats/top', { params: { days, limit } })

/** 单链统计 + 趋势：GET /api/stats/link/{code}?days=7 */
export const getLinkStats = (code, days = 7) =>
  http.get(`/stats/link/${code}`, { params: { days } })

/** 来源分布（饼图）：GET /api/stats/sources?days=7 */
export const getSources = (days = 7) => http.get('/stats/sources', { params: { days } })

/** 设备分布（柱状图）：GET /api/stats/devices?days=7 */
export const getDevices = (days = 7) => http.get('/stats/devices', { params: { days } })

/** 手动聚合某天（不传就是今天）：POST /api/stats/aggregate */
export const aggregate = (date) =>
  http.post('/stats/aggregate', null, { params: date ? { date } : {} })
