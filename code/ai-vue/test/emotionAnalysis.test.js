import test from 'node:test'
import assert from 'node:assert/strict'
import { mockAnalyzeEmotion } from '../src/api/mock.js'

test('emotion fallback does not infer signals from negated descriptions', async () => {
  const analysis = await mockAnalyzeEmotion('我没有什么压力，也不太焦虑，昨晚没觉得睡不着。')

  assert.equal(analysis.emotion, '暂不判断')
  assert.equal(analysis.evidence, '')
  assert.ok(analysis.cues.every((cue) => !cue.evidence))
  assert.equal(analysis.stress, undefined)
  assert.equal(analysis.anxiety, undefined)
  assert.equal(analysis.sleepRisk, undefined)
})

test('emotion fallback continues scanning after a negated first mention', async () => {
  const analysis = await mockAnalyzeEmotion('我不焦虑，后来还是有点焦虑。')

  assert.equal(analysis.emotion, '焦虑')
  assert.equal(analysis.evidence, '焦虑')
  assert.equal(analysis.cues.find((cue) => cue.label === '焦虑')?.evidence, '焦虑')
})
