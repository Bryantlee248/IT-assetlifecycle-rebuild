import { ref, type Ref } from 'vue'
import type { ViewStatus } from '../components/StateView.vue'

// 数据型页面的四态状态机：loading / empty / error / no-permission / ready
// 配合 <StateView> 组件使用，统一处理"加载中/无数据/出错可重试/无权限"。
export function useViewState() {
  const status = ref<ViewStatus>('loading')
  const errorDetail = ref('')

  function setLoading() {
    status.value = 'loading'
    errorDetail.value = ''
  }
  function setEmpty() {
    status.value = 'empty'
  }
  function setReady() {
    status.value = 'ready'
  }
  function setNoPermission() {
    status.value = 'no-permission'
  }
  function setError(detail = '') {
    status.value = 'error'
    errorDetail.value = detail
  }
  // 根据"是否含有数据"在 ready / empty 间二选一
  function settle(hasData: boolean) {
    status.value = hasData ? 'ready' : 'empty'
  }

  return {
    status: status as Ref<ViewStatus>,
    errorDetail,
    setLoading,
    setEmpty,
    setReady,
    setNoPermission,
    setError,
    settle
  }
}
