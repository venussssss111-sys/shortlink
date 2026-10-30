<script setup>
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { changePassword, logout as apiLogout, uploadAvatar } from '../api/auth'
import { auth, clearAuth, setUser } from '../store/auth'

const initial = computed(() => {
  const name = auth.user?.nickname || auth.user?.username || '?'
  return name.slice(0, 1).toUpperCase()
})

const uploading = ref(false)
const fileInput = ref(null)

const pwdVisible = ref(false)
const pwdForm = ref({ oldPassword: '', newPassword: '' })
const pwdLoading = ref(false)

function onCommand(cmd) {
  if (cmd === 'avatar') fileInput.value?.click()
  else if (cmd === 'password') {
    pwdForm.value = { oldPassword: '', newPassword: '' }
    pwdVisible.value = true
  } else if (cmd === 'logout') doLogout()
}

async function onFileChange(e) {
  const file = e.target.files?.[0]
  e.target.value = '' // 允许重复选同一个文件
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('只能上传图片')
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('头像不能超过 2MB')
    return
  }
  uploading.value = true
  try {
    const url = await uploadAvatar(file)
    setUser({ ...auth.user, avatar: url })
    ElMessage.success('头像已更新')
  } catch (err) {
    /* 已提示 */
  } finally {
    uploading.value = false
  }
}

async function submitPassword() {
  const { oldPassword, newPassword } = pwdForm.value
  if (!oldPassword || !newPassword) {
    ElMessage.warning('两个密码都要填')
    return
  }
  if (newPassword.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  pwdLoading.value = true
  try {
    await changePassword({ oldPassword, newPassword })
    ElMessage.success('密码已修改，请重新登录')
    pwdVisible.value = false
    await doLogout(true)
  } catch (err) {
    /* 已提示 */
  } finally {
    pwdLoading.value = false
  }
}

async function doLogout(silent = false) {
  if (!silent) {
    try {
      await ElMessageBox.confirm('退出后需要重新登录', '确认退出？', {
        confirmButtonText: '退出',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch (e) {
      return
    }
  }
  try {
    await apiLogout()
  } catch (e) {
    /* token 已经失效也算退出成功 */
  }
  clearAuth()
}
</script>

<template>
  <div class="userbox">
    <el-dropdown trigger="click" placement="bottom-end" @command="onCommand">
      <button class="avatarbtn" :class="{ busy: uploading }" title="账号">
        <img v-if="auth.user?.avatar" :src="auth.user.avatar" alt="" />
        <span v-else class="initial">{{ initial }}</span>
      </button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item disabled>
            {{ auth.user?.nickname || auth.user?.username }}
          </el-dropdown-item>
          <el-dropdown-item command="avatar" divided>上传头像</el-dropdown-item>
          <el-dropdown-item command="password">修改密码</el-dropdown-item>
          <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>

    <input
      ref="fileInput"
      type="file"
      accept="image/png,image/jpeg,image/gif,image/webp"
      style="display: none"
      @change="onFileChange"
    />

    <el-dialog v-model="pwdVisible" title="修改密码" width="380px" append-to-body>
      <div class="field">
        <span class="cap">原密码</span>
        <input v-model="pwdForm.oldPassword" class="input" type="password" placeholder="••••••" />
      </div>
      <div class="field">
        <span class="cap">新密码</span>
        <input v-model="pwdForm.newPassword" class="input" type="password" placeholder="至少 6 位" />
      </div>
      <template #footer>
        <button class="btn ghost" @click="pwdVisible = false">取消</button>
        <button class="btn primary" :disabled="pwdLoading" @click="submitPassword">
          {{ pwdLoading ? '提交中…' : '确认修改' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>
