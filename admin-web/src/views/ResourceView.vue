<script setup lang="ts">import {onMounted,reactive,ref,watch} from "vue";import {getPage} from "../api/request";const props=defineProps<{title:string;resource:string}>();const query=reactive({keyword:"",status:"",page:1,pageSize:20});const rows=ref<Record<string,unknown>[]>([]);const total=ref(0);const loading=ref(false);const error=ref("");async function load(){loading.value=true;error.value="";try{const result=await getPage(props.resource,query);rows.value=result.items;total.value=result.total;}catch(e){error.value=e instanceof Error?e.message:"加载失败";}finally{loading.value=false;}}function search(){query.page=1;load();}watch(()=>props.resource,()=>{query.page=1;load();});onMounted(load);</script>
<template>
  <section>
    <el-card shadow="never">
      <el-form inline>
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            clearable
            placeholder="名称或编号"
            @keyup.enter="search"
          />
        </el-form-item><el-form-item label="状态">
          <el-select
            v-model="query.status"
            clearable
            placeholder="全部"
          >
            <el-option
              label="启用"
              value="ACTIVE"
            /><el-option
              label="待处理"
              value="PENDING"
            />
          </el-select>
        </el-form-item><el-button
          type="primary"
          @click="search"
        >
          查询
        </el-button><el-button @click="Object.assign(query,{keyword:'',status:'',page:1});load()">
          重置
        </el-button>
      </el-form>
    </el-card><el-card
      class="table-card"
      shadow="never"
    >
      <template #header>
        <strong>{{ title }}</strong>
      </template><el-alert
        v-if="error"
        :title="error"
        type="error"
        show-icon
      >
        <el-button
          link
          @click="load"
        >
          重试
        </el-button>
      </el-alert><el-skeleton
        v-else-if="loading"
        :rows="8"
        animated
      /><el-empty
        v-else-if="!rows.length"
        description="暂无数据"
      /><template v-else>
        <el-table :data="rows">
          <el-table-column
            prop="id"
            label="ID"
            width="90"
          /><el-table-column
            prop="name"
            label="名称"
            min-width="180"
          /><el-table-column
            prop="status"
            label="状态"
            width="140"
          /><el-table-column
            prop="updatedAt"
            label="更新时间"
            width="190"
          /><el-table-column
            label="操作"
            width="120"
          >
            <template #default>
              <el-button
                link
                type="primary"
              >
                查看
              </el-button>
            </template>
          </el-table-column>
        </el-table><el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10,20,50,100]"
          layout="total, sizes, prev, pager, next"
          @change="load"
        />
      </template>
    </el-card>
  </section>
</template>
