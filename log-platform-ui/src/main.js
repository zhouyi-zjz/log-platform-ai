import { createApp } from 'vue'
import {
  ElButton,
  ElDatePicker,
  ElDrawer,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElIcon,
  ElInput,
  ElLoading,
  ElOption,
  ElSelect
} from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import './styles/index.scss'

const app = createApp(App)

;[
  ElButton,
  ElDatePicker,
  ElDrawer,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElIcon,
  ElInput,
  ElOption,
  ElSelect
].forEach((component) => app.component(component.name, component))

app.directive('loading', ElLoading.directive)
app.mount('#app')
