import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * Excel 下载助手
 * 后端导出接口直接返回文件流（不是 Result JSON 包装），所以用独立的 axios 调用，
 * 拿到 blob 后创建 <a> 标签触发浏览器下载
 * @param {string} url    接口地址，如 /analysis/worktime/export
 * @param {object} params 查询参数
 * @param {string} filename 保存的文件名
 */
export async function download(url, params, filename) {
  try {
    const res = await axios.get('/api' + url, {
      params,
      responseType: 'blob',
      headers: { Authorization: localStorage.getItem('token') || '' }
    })
    const blob = new Blob([res.data])
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = filename
    link.click()
    URL.revokeObjectURL(link.href)
  } catch (e) {
    ElMessage.error('导出失败，请稍后重试')
  }
}
