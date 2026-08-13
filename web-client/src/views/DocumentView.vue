<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listDocuments, uploadDocument } from '../api/document'
import { formatApiError } from '../api/http'
import { authState, clearSession } from '../auth/session'
import router from '../router'
import type { DocumentData, DocumentIndexStatus } from '../types/api'

const MAX_FILE_BYTES = 20 * 1024 * 1024

const statusDetails: Record<DocumentIndexStatus, { label: string; description: string }> = {
  UPLOADED: { label: '已上传', description: '原文件已安全保存，等待索引。' },
  INDEXING: { label: '索引中', description: 'Java 已提交 Python 构建索引。' },
  INDEXED: { label: '可检索', description: '知识索引已经完成。' },
  FAILED: { label: '索引失败', description: '后端已确认索引失败。' },
  UNKNOWN: { label: '结果待确认', description: '结果暂时无法确认，请勿自动重新上传。' },
  DELETED: { label: '已删除', description: '该文档已被后端标记删除。' },
}

const documents = ref<DocumentData[]>([])
const selectedFile = ref<File | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const loadingDocuments = ref(true)
const uploading = ref(false)
const uploadProgress = ref<number | null>(null)
const errorMessage = ref('')
const successMessage = ref('')
const traceId = ref<string | null>(null)

const canUpload = computed(() => selectedFile.value !== null && !uploading.value)

function clearMessages(): void {
  errorMessage.value = ''
  successMessage.value = ''
  traceId.value = null
}

function showError(error: unknown): void {
  const problem = formatApiError(error)
  errorMessage.value = problem.message
  traceId.value = problem.traceId
}

function validateFile(file: File): string | null {
  if (file.size === 0) return '请选择非空 PDF 文件。'
  if (file.size > MAX_FILE_BYTES) return 'PDF 不能超过 20 MiB。'
  if (!file.name.toLowerCase().endsWith('.pdf') || file.type !== 'application/pdf') {
    return '只支持扩展名和 MIME 均为 PDF 的文件。'
  }
  return null
}

function selectFile(event: Event): void {
  if (uploading.value) return
  clearMessages()
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  if (!file) {
    selectedFile.value = null
    return
  }
  const validationError = validateFile(file)
  if (validationError) {
    selectedFile.value = null
    input.value = ''
    errorMessage.value = validationError
    return
  }
  selectedFile.value = file
}

async function loadDocumentList(preserveMessages = false): Promise<void> {
  loadingDocuments.value = true
  if (!preserveMessages) clearMessages()
  try {
    const page = await listDocuments()
    documents.value = page.items
  } catch (error) {
    if (!preserveMessages) showError(error)
  } finally {
    loadingDocuments.value = false
  }
}

async function submitUpload(): Promise<void> {
  const file = selectedFile.value
  if (!file || uploading.value) return

  uploading.value = true
  uploadProgress.value = null
  clearMessages()
  try {
    const uploaded = await uploadDocument(file, (percentage) => {
      uploadProgress.value = percentage
    })
    documents.value = [
      uploaded,
      ...documents.value.filter((item) => item.documentId !== uploaded.documentId),
    ]
    successMessage.value = `${uploaded.originalFilename} 已提交，当前状态：${statusDetails[uploaded.indexStatus].label}。`
    selectedFile.value = null
    if (fileInput.value) fileInput.value.value = ''
    await loadDocumentList(true)
  } catch (error) {
    showError(error)
    await loadDocumentList(true)
  } finally {
    uploading.value = false
    uploadProgress.value = null
  }
}

function formatBytes(size: number): string {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KiB`
  return `${(size / (1024 * 1024)).toFixed(1)} MiB`
}

function formatDate(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

async function openChat(): Promise<void> {
  await router.push({ name: 'chat' })
}

async function logout(): Promise<void> {
  clearSession()
  await router.replace({ name: 'login' })
}

onMounted(() => loadDocumentList())
</script>

<template>
  <main class="document-page">
    <header class="document-header">
      <div>
        <p class="eyebrow accent">INSURANCE AI · KNOWLEDGE</p>
        <h1>PDF 知识库</h1>
        <p>原文件和业务状态由 Java 管理，向量索引由 Python 构建。</p>
      </div>
      <div class="user-menu">
        <span class="status-dot"></span>
        <span>{{ authState.user.username }}</span>
        <button type="button" class="header-link" @click="openChat">返回聊天</button>
        <button type="button" @click="logout">退出</button>
      </div>
    </header>

    <section class="document-content">
      <div v-if="errorMessage" class="workspace-error document-notice" role="alert">
        <span>!</span>
        <div>
          <strong>{{ errorMessage }}</strong>
          <small v-if="traceId">TraceId：{{ traceId }}</small>
        </div>
        <button type="button" aria-label="关闭错误提示" @click="clearMessages">×</button>
      </div>

      <div v-if="successMessage" class="success-banner" role="status">
        {{ successMessage }}
      </div>

      <form class="upload-card" @submit.prevent="submitUpload">
        <div>
          <p class="eyebrow accent">UPLOAD PDF</p>
          <h2>添加保险条款</h2>
          <p>单个 PDF，最大 20 MiB。浏览器校验只用于快速反馈，最终以 Java 校验为准。</p>
        </div>
        <label class="file-picker" :class="{ disabled: uploading }" for="pdf-file">
          <input
            id="pdf-file"
            ref="fileInput"
            type="file"
            accept=".pdf,application/pdf"
            :disabled="uploading"
            @change="selectFile"
          />
          <strong>{{ selectedFile?.name || '选择 PDF 文件' }}</strong>
          <span>{{ selectedFile ? formatBytes(selectedFile.size) : '点击浏览本地文件' }}</span>
        </label>
        <div class="upload-actions">
          <div class="upload-progress-copy" aria-live="polite">
            <template v-if="uploading">
              <strong>正在上传并等待索引结果</strong>
              <span>{{ uploadProgress === null ? '处理中…' : `已发送 ${uploadProgress}%` }}</span>
            </template>
            <span v-else>请求不会自动重发；进度 100% 不代表索引已经完成。</span>
          </div>
          <button class="primary-button upload-button" type="submit" :disabled="!canUpload">
            {{ uploading ? '处理中…' : '上传并建立索引' }}
          </button>
        </div>
        <progress
          v-if="uploading && uploadProgress !== null"
          class="upload-progress"
          :value="uploadProgress"
          max="100"
        >{{ uploadProgress }}%</progress>
      </form>

      <section class="document-list-card">
        <div class="section-heading">
          <div>
            <p class="eyebrow accent">DOCUMENTS</p>
            <h2>我的文档</h2>
          </div>
          <button type="button" :disabled="loadingDocuments || uploading" @click="loadDocumentList()">
            刷新状态
          </button>
        </div>

        <div v-if="loadingDocuments" class="document-empty" aria-live="polite">
          <span class="loading-orbit"></span>
          <p>正在读取 Java 文档状态…</p>
        </div>
        <div v-else-if="documents.length === 0" class="document-empty">
          <strong>还没有文档</strong>
          <p>选择一个 PDF，建立第一份可检索知识。</p>
        </div>
        <ul v-else class="document-list">
          <li v-for="document in documents" :key="document.documentId" class="document-item">
            <div class="document-symbol">PDF</div>
            <div class="document-copy">
              <strong>{{ document.originalFilename }}</strong>
              <span>{{ formatBytes(document.sizeBytes) }} · {{ formatDate(document.createdAt) }}</span>
              <small>{{ statusDetails[document.indexStatus].description }}</small>
            </div>
            <span class="status-badge" :data-status="document.indexStatus">
              {{ statusDetails[document.indexStatus].label }}
            </span>
          </li>
        </ul>
      </section>
    </section>
  </main>
</template>
