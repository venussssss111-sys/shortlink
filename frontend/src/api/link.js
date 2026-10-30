import http from './http'

/** 短链访问地址的后端前缀。部署到别的域名时改这里（或加 .env 里的 VITE_SHORT_BASE）。 */
export const SHORT_BASE = import.meta.env.VITE_SHORT_BASE || 'http://localhost:8080'

export const shortUrlOf = (code) => `${SHORT_BASE}/${code}`

/** 分页查询：GET /api/links?page=1&size=10&keyword= */
export const pageLinks = (params) => http.get('/links', { params })

/** 详情：GET /api/links/{code} */
export const getLink = (code) => http.get(`/links/${code}`)

/** 新建：POST /api/links */
export const createLink = (data) => http.post('/links', data)

/** 启用/停用：PATCH /api/links/{code}/status?status=1 */
export const updateStatus = (code, status) =>
  http.patch(`/links/${code}/status`, null, { params: { status } })

/** 删除：DELETE /api/links/{code} */
export const deleteLink = (code) => http.delete(`/links/${code}`)
