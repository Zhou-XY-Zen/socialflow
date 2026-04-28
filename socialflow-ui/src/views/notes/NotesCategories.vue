<!--
  NotesCategories.vue —— 分类管理
-->

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { noteCategoryApi } from '@/api/note'
import type { NoteCategoryVO } from '@/types/api'

const cats = ref<NoteCategoryVO[]>([])

async function load() {
  cats.value = await noteCategoryApi.tree()
}
onMounted(load)

const newCatName = ref('')
const newCatParent = ref<string | undefined>()

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
</script>

<template>
  <div class="cats">
    <PageHeader title="分类" subtitle="管理笔记的分类树（最多两级）"
                icon="CollectionTag" />

    <el-card shadow="never">
      <div class="add-row">
        <el-input v-model="newCatName" placeholder="新分类名" style="flex:1" />
        <el-select v-model="newCatParent" placeholder="父分类（可空 = 顶级）" clearable style="width:200px">
          <el-option v-for="c in cats" :key="c.id" :value="c.id" :label="c.name" />
        </el-select>
        <el-button type="primary" @click="addCat">添加</el-button>
      </div>
      <el-tree :data="cats" :props="{ label: 'name', children: 'children' }" node-key="id">
        <template #default="{ data }">
          <span class="tree-node">
            <span>{{ data.name }} <small>({{ data.noteCount ?? 0 }})</small></span>
            <el-button link size="small" type="danger" @click="delCat(data)">删除</el-button>
          </span>
        </template>
      </el-tree>
    </el-card>
  </div>
</template>

<style scoped>
.cats { padding: 16px; max-width: 720px; }
.add-row { display: flex; gap: 8px; margin-bottom: 12px; }
.tree-node { display: flex; justify-content: space-between; align-items: center;
             width: 100%; padding-right: 8px; }
</style>
