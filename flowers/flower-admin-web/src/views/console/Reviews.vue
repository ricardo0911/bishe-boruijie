<template>
  <div>
    <div class="top-bar">
      <h1 class="page-title">评论管理</h1>
      <div class="review-actions">
        <button class="btn btn-outline" @click="loadReviews">刷新</button>
      </div>
    </div>

    <div class="card">
      <div class="card-body review-filters">
        <div class="review-filter-row">
          <input
            v-model.trim="keyword"
            class="form-control review-keyword-input"
            placeholder="搜索订单号 / 用户名 / 商品名 / 评论内容"
          >
          <select v-model="scoreFilter" class="form-control review-score-select">
            <option value="">全部评分</option>
            <option v-for="score in [5, 4, 3, 2, 1]" :key="score" :value="String(score)">{{ score }} 星</option>
          </select>
          <button class="btn btn-outline" @click="resetFilters">重置</button>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-header">
        <span class="card-title">评论列表</span>
        <span style="font-size:12px;color:var(--text-muted)">共 {{ filteredReviews.length }} 条</span>
      </div>
      <div class="card-body">
        <div class="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>用户</th>
                <th>商品</th>
                <th>订单号</th>
                <th>评分</th>
                <th>评论 / 回复</th>
                <th>图片</th>
                <th>时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading"><td colspan="9" class="loading">加载中...</td></tr>
              <tr v-else-if="filteredReviews.length === 0"><td colspan="9" class="empty">暂无评论</td></tr>
              <tr v-for="review in filteredReviews" :key="review.id" v-else>
                <td class="review-mono">#{{ review.id }}</td>
                <td>{{ review.userName || '-' }}</td>
                <td>
                  <div class="review-product">{{ review.productTitle || '-' }}</div>
                </td>
                <td class="review-mono">{{ review.orderNo || '-' }}</td>
                <td>
                  <div class="review-score">{{ renderStars(review.score) }}</div>
                  <div class="review-score-text">{{ review.score || 0 }} 星</div>
                </td>
                <td>
                  <div class="review-content" :title="review.content || ''">{{ sliceText(review.content) }}</div>
                  <div v-if="review.reply" class="review-reply">
                    <div class="review-reply-title">商家回复</div>
                    <div class="review-reply-content">{{ review.reply }}</div>
                  </div>
                </td>
                <td>
                  <div v-if="review.images.length > 0" class="review-image-list">
                    <img v-for="(img, index) in review.images" :key="`${review.id}-${index}`" class="review-image" :src="img" alt="review-image">
                  </div>
                  <span v-else class="review-time">-</span>
                </td>
                <td class="review-time">
                  <div>{{ formatTime(review.createTime || review.createdAt) }}</div>
                  <div v-if="review.replyTime">回复：{{ formatTime(review.replyTime) }}</div>
                </td>
                <td>
                  <div class="review-op-list">
                    <button class="btn btn-sm btn-outline" :disabled="replyingId === review.id" @click="replyReview(review)">
                      {{ review.reply ? '修改回复' : '回复' }}
                    </button>
                    <button class="btn btn-sm btn-danger" :disabled="deletingId === review.id" @click="removeReview(review)">
                      {{ deletingId === review.id ? '删除中...' : '删除' }}
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from '../../api'
import { showToast, resolveImageUrl } from '../../utils'

const loading = ref(false)
const deletingId = ref(0)
const replyingId = ref(0)
const reviews = ref([])
const keyword = ref('')
const scoreFilter = ref('')

const filteredReviews = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return reviews.value.filter((item) => {
    const matchesKeyword = !kw || [
      item.userName,
      item.productTitle,
      item.orderNo,
      item.content,
      item.reply
    ].some((field) => String(field || '').toLowerCase().includes(kw))

    const matchesScore = !scoreFilter.value || String(item.score || '') === scoreFilter.value
    return matchesKeyword && matchesScore
  })
})

function normalizeReview(item) {
  return {
    ...item,
    images: Array.isArray(item.images) ? item.images.map((img) => resolveImageUrl(img)) : [],
  }
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ').substring(0, 16) : '-'
}

function sliceText(text) {
  const value = String(text || '')
  if (!value) return '-'
  return value.length > 40 ? `${value.slice(0, 40)}...` : value
}

function renderStars(score) {
  const safeScore = Math.max(0, Math.min(5, Number(score || 0)))
  return `${'★'.repeat(safeScore)}${'☆'.repeat(5 - safeScore)}`
}

function resetFilters() {
  keyword.value = ''
  scoreFilter.value = ''
}

async function loadReviews() {
  loading.value = true
  try {
    const res = await api.listReviews()
    if (!res.success) {
      showToast(res.message || '评论加载失败')
      reviews.value = []
      return
    }
    reviews.value = Array.isArray(res.data) ? res.data.map((item) => normalizeReview(item)) : []
  } catch (error) {
    reviews.value = []
    showToast('评论加载失败')
  } finally {
    loading.value = false
  }
}

async function replyReview(review) {
  if (!review?.id || replyingId.value) return
  const nextReply = window.prompt('请输入商家回复内容', review.reply || '')
  if (nextReply === null) return

  replyingId.value = review.id
  try {
    const res = await api.replyReview(review.id, nextReply)
    if (!res.success) {
      showToast(res.message || '回复失败')
      return
    }
    showToast('回复已保存')
    await loadReviews()
  } catch (error) {
    showToast('回复失败，请稍后重试')
  } finally {
    replyingId.value = 0
  }
}

async function removeReview(review) {
  if (!review?.id || deletingId.value) return
  const productTitle = review.productTitle || '该评论'
  if (!window.confirm(`确定删除「${productTitle}」的评论吗？`)) {
    return
  }

  deletingId.value = review.id
  try {
    const res = await api.deleteReview(review.id)
    if (!res.success) {
      showToast(res.message || '删除失败')
      return
    }
    reviews.value = reviews.value.filter((item) => item.id !== review.id)
    showToast('评论已删除')
  } catch (error) {
    showToast('删除失败，请稍后重试')
  } finally {
    deletingId.value = 0
  }
}

onMounted(() => {
  loadReviews()
})
</script>

<style scoped>
.review-actions,
.review-filters,
.review-filter-row,
.review-op-list {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.review-filters {
  flex-direction: column;
  align-items: stretch;
}

.review-keyword-input {
  min-width: 320px;
  flex: 1;
}

.review-score-select {
  width: 140px;
}

.review-mono {
  font-family: monospace;
  font-size: 12px;
}

.review-product {
  font-weight: 600;
  color: var(--text-primary);
}

.review-score {
  font-size: 14px;
  color: #f5b400;
}

.review-score-text,
.review-time {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-muted);
}

.review-content {
  max-width: 320px;
  line-height: 1.7;
  color: var(--text-secondary);
}

.review-reply {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(255, 244, 246, 0.8);
}

.review-reply-title {
  font-size: 12px;
  color: #bb5476;
  font-weight: 700;
}

.review-reply-content {
  margin-top: 6px;
  color: var(--text-secondary);
  line-height: 1.7;
}

.review-image-list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.review-image {
  width: 52px;
  height: 52px;
  border-radius: 10px;
  object-fit: cover;
  border: 1px solid rgba(0, 0, 0, 0.06);
}

@media (max-width: 960px) {
  .review-keyword-input {
    min-width: 0;
    width: 100%;
  }

  .review-score-select {
    width: 100%;
  }
}
</style>