<!--
  NotesCategories.vue —— 分类与标签管理（极简版）
-->

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { noteCategoryApi, noteTagApi } from '@/api/note'
import type { NoteCategoryVO, NoteTagVO } from '@/types/api'

const cats = ref<NoteCategoryVO[]>([])
const tags = ref<NoteTagVO[]>([])

async function load() {
  const [c, t] = await Promise.all([noteCategoryApi.tree(), noteTagApi.list()])
  cats.value = c
  tags.value = t
}
onMounted(load)

const newCatName = ref('')
const newCatParent = ref<number | undefined>()

async function addCat() {
  if (!newCatName.value.trim()) return ElMessage.warning('请输入分类名')
  await noteCategoryApi.create({
    name: newCatName.value.trim(),
    parentId: newCatParent.value,
  })
  newCatName.value = ''
  newCatParent.value = undefined
  await load()
  ElMessage.success('已创建')
}

async function delCat(c: NoteCategoryVO) {
  try {
    await ElMessageBox.confirm(`删除分类「${c.name}」？关联笔记的分类会清空，但笔记保留。`,
                                '确认', { type: 'warning' })
    await noteCategoryApi.delete(c.id)
    await load()
  } catch {/**/}
}

async function delTag(t: NoteTagVO) {
  try {
    await ElMessageBox.confirm(`删除标签「${t.name}」？将解除所有笔记的关联。`,
                                '确认', { type: 'warning' })
    await noteTagApi.delete(t.id)
    await load()
  } catch {/**/}
}

async function renameTag(t: NoteTagVO) {
  try {
    const r = await ElMessageBox.prompt('新标签名', '重命名', { inputValue: t.name })
    if (r.value && r.value !== t.name) {
      await noteTagApi.rename(t.id, r.value)
      await load()
    }
  } catch {/**/}
}
</script>

<template>
  <div class="cats">
    <PageHeader title="分类与标签" subtitle="管理笔记的分类树与标签"
                icon="CollectionTag" />

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>分类</template>
          <div class="add-row">
            <el-input v-model="newCatName" placeholder="新分类名" style="flex:1" />
            <el-select v-model="newCatParent" placeholder="父分类（可空 = 顶级）" clearable style="width:200px">
              <el-option v-for="c in cats" :key="c.id" :value="c.id" :label="c.name" />
            </el-select>
            <el-button type="primary" @click="addCat">添加</el-button>
          </div>
          <el-tree :data="cats" :props="{ label: 'name', children: 'children' }" node-key="id">
            <template #default="{ node, data }">
              <span class="tree-node">
                <span>{{ data.name }} <small>({{ data.noteCount ?? 0 }})</small></span>
                <el-button link size="small" type="danger" @click="delCat(data)">删除</el-button>
              </span>
            </template>
          </el-tree>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>标签</template>
          <div class="tag-cloud">
            <span v-for="t in tags" :key="t.id" class="tag-row">
              <el-tag size="large" closable @close="delTag(t)" @click="renameTag(t)">
                {{ t.name }}
                <small>· {{ t.usageCount ?? 0 }}</small>
              </el-tag>
            </span>
            <span v-if="tags.length === 0" class="empty">暂无标签</span>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.cats { padding: 16px; }
.add-row { display: flex; gap: 8px; margin-bottom: 12px; }
.tree-node { display: flex; justify-content: space-between; align-items: center; width: 100%; padding-right: 8px; }
.tag-cloud { display: flex; flex-wrap: wrap; gap: 8px; }
.tag-row { display: inline-block; }
.empty { color: #9ca3af; font-size: 13px; }
</style>
