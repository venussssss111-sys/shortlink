import http from './http'

/** 登录：POST /api/auth/login → { token, user } */
export const login = (data) => http.post('/auth/login', data)

/** 注册：POST /api/auth/register → { token, user } */
export const register = (data) => http.post('/auth/register', data)

/** 退出：POST /api/auth/logout（把当前 token 拉黑） */
export const logout = () => http.post('/auth/logout')

/** 当前用户：GET /api/auth/me */
export const me = () => http.get('/auth/me')

/** 改密码：POST /api/auth/password */
export const changePassword = (data) => http.post('/auth/password', data)

/** 上传头像：POST /api/auth/avatar（multipart，字段名 file）→ 头像地址 */
export const uploadAvatar = (file) => {
  const form = new FormData()
  form.append('file', file)
  return http.post('/auth/avatar', form)
}
