import { Chip } from '@mui/material'
import type { CaseState } from '../types'

const BG: Record<CaseState, string> = {
  OPEN:          '#1565c0',
  INVESTIGATING: '#6a1b9a',
  NEEDS_SOURCE:  '#e65100',
  RESOLVED:      '#2e7d32',
  REOPENED:      '#c62828',
}

export default function StateChip({ state }: { state: CaseState }) {
  return (
    <Chip
      label={state.replace('_', ' ')}
      size="small"
      sx={{ backgroundColor: BG[state], color: '#fff', fontWeight: 600, fontSize: '0.7rem' }}
    />
  )
}
