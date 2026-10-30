<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createLink, shortUrlOf } from '../api/link'

const props = defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue', 'created'])

const formRef = ref()
const submitting = ref(false)

const blank = () => ({ originUrl: '', customCode: '', expireDays: null })
const form = reactive(blank())

const rules = {
  originUrl: [
    { required: true, message: '原始链接不能为空', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!value) return callback()
        if (!/^https?:\/\//i.test(value)) {
          return callback(new Error('必须以 http:// 或 https:// 开头'))
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  customCode: [
    {
      validator: (_rule, value, callback) => {
        // 留空 = 随机生成。注意不能把 "" 发给后端：后端 @Pattern 会把空串当不合法。
        if (!value) return callback()
        if (!/^[a-zA-Z0-9]{4,10}$/.test(value)) {
          return callback(new Error('4-10 位，只能字母或数字'))
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      Object.assign(form, blank())
      formRef.value?.clearValidate()
    }
  }
)

function close() {
  emit('update:modelValue', false)
}

async function submit() {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }

  const payload = { originUrl: form.originUrl.trim() }
  if (form.customCode && form.customCode.trim()) payload.customCode = form.customCode.trim()
  if (form.expireDays) payload.expireDays = form.expireDays

  submitting.value = true
  try {
    const data = await createLink(payload)
    ElMessage.success(`创建成功：${data.shortUrl || shortUrlOf(data.code)}`)
    emit('created', data)
    close()
  } catch (e) {
    // 409（短码被占用）之类的提示由拦截器负责
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="新建短链"
    width="520px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
      <el-form-item label="原始链接" prop="originUrl">
        <el-input v-model="form.originUrl" placeholder="https://example.com/很长的/链接" />
      </el-form-item>

      <el-form-item label="自定义短码" prop="customCode">
        <el-input v-model="form.customCode" placeholder="留空则随机生成" />
        <div class="hint-text">4-10 位字母或数字；被别人用了会提示冲突</div>
      </el-form-item>

      <el-form-item label="有效期">
        <el-input-number
          v-model="form.expireDays"
          :min="1"
          :max="3650"
          :controls="false"
          placeholder="天"
          style="width: 120px"
        />
        <span class="hint-text">天，留空 = 永久</span>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">创建</el-button>
    </template>
  </el-dialog>
</template>
