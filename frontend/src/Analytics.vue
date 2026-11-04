<template>
  <section class="analytics">
    <p v-if="loading">Loading analytics...</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="!data || !data.weekly.length" class="card empty">You have no posts yet, so there is nothing to analyse.</p>

    <template v-else>
      <div class="summary">
        <div class="card stat">
          <span class="label">Total likes</span>
          <strong>{{ data.totalLikes }}</strong>
        </div>
        <div class="card stat">
          <span class="label">Total comments</span>
          <strong>{{ data.totalComments }}</strong>
        </div>
        <div class="card stat">
          <span class="label">Like-to-comment ratio</span>
          <strong>{{ data.likeToCommentRatio == null ? '–' : data.likeToCommentRatio.toFixed(2) }}</strong>
        </div>
        <div class="card stat">
          <span class="label">Engagement rank</span>
          <strong>Top {{ data.rank.topPercent }}%</strong>
          <small>{{ data.rank.averageEngagement.toFixed(1) }} likes + comments per post, among {{ data.rank.totalAuthors }} authors</small>
        </div>
      </div>

      <div class="card">
        <h3>Per week</h3>
        <table>
          <thead>
            <tr><th>Week starting</th><th>Posts</th><th>Likes</th><th>Comments</th></tr>
          </thead>
          <tbody>
            <tr v-for="w in data.weekly" :key="w.weekStart">
              <td>{{ w.weekStart }}</td><td>{{ w.posts }}</td><td>{{ w.likes }}</td><td>{{ w.comments }}</td>
            </tr>
          </tbody>
        </table>
        <small>Likes and comments are counted for the week their post was created.</small>
      </div>

      <div class="card">
        <h3>Top 5 posts by engagement</h3>
        <ol class="top">
          <li v-for="p in data.topPosts" :key="p.postId">
            <span class="content">{{ p.content }}</span>
            <span class="metrics">{{ p.likes }} likes + {{ p.comments }} comments = <b>{{ p.engagement }}</b></span>
          </li>
        </ol>
      </div>

      <div class="card">
        <h3>Average likes per post: photos vs videos</h3>
        <p v-if="!data.averageLikesByMediaType.length">No posts with media yet.</p>
        <div v-for="m in data.averageLikesByMediaType" :key="m.mediaType" class="bar-row">
          <span class="bar-label">{{ m.mediaType }} ({{ m.posts }})</span>
          <div class="bar"><div class="fill" :style="{ width: barWidth(m.averageLikes) }"></div></div>
          <span class="bar-value">{{ m.averageLikes.toFixed(2) }}</span>
        </div>
      </div>
    </template>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { fetchMyAnalytics } from './api'

const data = ref(null)
const loading = ref(true)
const error = ref('')

const maxAverage = computed(() => Math.max(0, ...(data.value?.averageLikesByMediaType ?? []).map(m => m.averageLikes)))

function barWidth(value) {
  return maxAverage.value > 0 ? `${(value / maxAverage.value) * 100}%` : '0%'
}

onMounted(async () => {
  try {
    data.value = await fetchMyAnalytics()
  } catch (e) {
    error.value = e.response?.data?.detail || 'Could not load analytics'
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.analytics { display: flex; flex-direction: column; gap: 1rem; }
.card { background: #fff; border-radius: 16px; padding: 1.5rem; box-shadow: 0 10px 30px rgba(17, 24, 39, 0.1); }
.card h3 { margin-top: 0; }
.summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 1rem; }
.stat { display: flex; flex-direction: column; gap: 0.25rem; }
.stat strong { font-size: 1.8rem; }
.label, small { color: #6b7280; }
table { width: 100%; border-collapse: collapse; margin-bottom: 0.5rem; }
th, td { text-align: left; padding: 0.4rem 0.5rem; border-bottom: 1px solid #e5e7eb; }
.top { padding-left: 1.2rem; margin: 0; }
.top li { margin-bottom: 0.6rem; }
.top .content { display: block; }
.top .metrics { color: #6b7280; font-size: 0.9rem; }
.bar-row { display: flex; align-items: center; gap: 0.75rem; margin-bottom: 0.5rem; }
.bar-label { width: 110px; text-transform: capitalize; }
.bar { flex: 1; height: 14px; background: #e5e7eb; border-radius: 999px; overflow: hidden; }
.fill { height: 100%; background: #111827; }
.bar-value { width: 50px; text-align: right; }
.error { color: #ef4444; }
</style>
