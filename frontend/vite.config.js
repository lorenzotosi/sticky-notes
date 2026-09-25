// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  optimizeDeps: {
    include: ['sticky-notes-domain']
  },
  test: {
    environment: 'jsdom',
    server: {
      deps: {
        inline: ['sticky-notes-domain']
      }
    }
  }
})
