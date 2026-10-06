import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const styles = readFileSync(resolve(process.cwd(), 'src/styles.css'), 'utf8')

test('fenced code blocks override inline code colors for readable contrast', () => {
  assert.match(styles, /\.message-content \.code-block code\s*\{[^}]*background:\s*transparent;/s)
  assert.match(styles, /\.message-content \.code-block code\s*\{[^}]*color:\s*#e5e7eb;/s)
})
