// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: 2026 Lorenzo Tosi, Alessandro Stefani

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  optimizeDeps: {
    include: ['sticky-notes-domain']
  },
  test: {
    environment: 'jsdom',
    server: {
      deps: {
        inline: ['sticky-notes-domain']
      }
    },
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov', 'clover'],
      reportsDirectory: './coverage',
      include: ['src/**/*.{js,vue}'],
      exclude: [
        'src/main.js',
        'src/**/*.test.js'
      ],
      thresholds: {
        lines: 80
      }
    }
  }
})
