import { Chip } from '@mui/material'
import type { CaseSeverity } from '../types'

const COLOR_MAP: Record<CaseSeverity, 'error' | 'warning' | 'info' | 'default'> = {
  CRITICAL: 'error',
  HIGH:     'error',
  MEDIUM:   'warning',
  LOW:      'info',
}

const BG_MAP: Record<CaseSeverity, string> = {
  CRITICAL: '#b71c1c',
  HIGH:     '#c62828',
  MEDIUM:   '#e65100',
  LOW:      '#1565c0',
}

export default function SeverityChip({ severity }: { severity: CaseSeverity }) {
  return (
    <Chip
      label={severity}
      size="small"
      sx={{ backgroundColor: BG_MAP[severity], color: '#fff', fontWeight: 700, fontSize: '0.7rem' }}
    />
  )
}
