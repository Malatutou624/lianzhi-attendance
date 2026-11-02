import request from './request'

/** 知识库管理 API */

// ===== 分类 =====
export const listCategories = () => request.get('/knowledge/categories')
export const saveCategory = (data) => request.post('/knowledge/categories', data)
export const deleteCategory = (id) => request.delete('/knowledge/categories/' + id)

// ===== 文档 =====
export const uploadDocument = (formData) => request.post('/knowledge/documents/upload', formData, {
  headers: { 'Content-Type': 'multipart/form-data' }
})
export const pageDocuments = (params) => request.get('/knowledge/documents', { params })
export const getDocument = (id) => request.get('/knowledge/documents/' + id)
export const deleteDocument = (id) => request.delete('/knowledge/documents/' + id)
export const reprocessDocument = (id) => request.post('/knowledge/documents/' + id + '/reprocess')

// ===== 切片 =====
export const listChunks = (docId) => request.get('/knowledge/chunks', { params: { docId } })
export const updateChunk = (id, data) => request.put('/knowledge/chunks/' + id, data)
export const deleteChunk = (id) => request.delete('/knowledge/chunks/' + id)

// ===== 入库任务 =====
export const listTasks = () => request.get('/knowledge/tasks')
export const getTask = (id) => request.get('/knowledge/tasks/' + id)
export const retryTask = (id) => request.post('/knowledge/tasks/' + id + '/retry')
export const cancelTask = (id) => request.post('/knowledge/tasks/' + id + '/cancel')
