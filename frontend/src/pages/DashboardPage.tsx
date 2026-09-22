import { useEffect, useState } from 'react'
import {
  Grid, Paper, Typography, Box, CircularProgress,
  Alert, Divider, Tooltip, Chip,
} from '@mui/material'
import CheckCircleIcon  from '@mui/icons-material/CheckCircle'
import ErrorIcon        from '@mui/icons-material/Error'
import HourglassIcon    from '@mui/icons-material/HourglassEmpty'
import { getMetrics } from '../api'
import type { MetricsSnapshot } from '../types'

const SEVERITY_BG: Record<string, string> = {
  CRITICAL: '#b71c1c',
  HIGH:     '#c62828',
  MEDIUM:   '#e65100',
  LOW:      '#1565c0',
}

export default function DashboardPage() {
  const [metrics, setMetrics] = useState<MetricsSnapshot | null>(null)
  const [error,   setError]   = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    getMetrics()
      .then(setMetrics)
      .catch(() => setError('Failed to load metrics'))
      .finally(() => setLoading(false))
  }, [])

  if (loading) return <Box sx={{ display: 'flex', justifyContent: 'center', mt: 8 }}><CircularProgress /></Box>
  if (error)   return <Alert severity="error">{error}</Alert>
  if (!metrics) return null

  const freshness = Object.entries(metrics.streamFreshness)

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} gutterBottom>
        Operations Dashboard
      </Typography>
      <Typography variant="caption" color="text.secondary">
        Collected at: {new Date(metrics.collectedAt).toLocaleString()}
      </Typography>

      {/* Open cases by severity */}
      <Typography variant="subtitle1" fontWeight={600} sx={{ mt: 3, mb: 1 }}>
        Open Cases by Severity
        <Tooltip title="Count of non-RESOLVED cases visible to your role. Definitions: CRITICAL = access/corruption, HIGH = confirmed financial mismatch, MEDIUM = stale/missing evidence, LOW = informational.">
          <span style={{ marginLeft: 6, cursor: 'help', color: '#888' }}>ⓘ</span>
        </Tooltip>
      </Typography>
      <Grid container spacing={2} sx={{ mb: 3 }}>
        {Object.entries(metrics.openCasesBySeverity).map(([sev, count]) => (
          <Grid item xs={6} sm={3} key={sev}>
            <Paper elevation={3} sx={{ p: 2, textAlign: 'center', borderTop: `4px solid ${SEVERITY_BG[sev]}` }}>
              <Typography variant="h3" fontWeight={800} color={SEVERITY_BG[sev]}>
                {count}
              </Typography>
              <Typography variant="body2" fontWeight={600}>{sev}</Typography>
            </Paper>
          </Grid>
        ))}
        <Grid item xs={12} sm={4}>
          <Paper elevation={3} sx={{ p: 2, textAlign: 'center', borderTop: '4px solid #1565c0' }}>
            <Typography variant="h3" fontWeight={800} color="#1565c0">
              {metrics.totalOpenCases}
            </Typography>
            <Typography variant="body2" fontWeight={600}>Total Open</Typography>
          </Paper>
        </Grid>
      </Grid>

      {/* Import queue health */}
      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={4}>
          <Paper elevation={2} sx={{ p: 2, display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <HourglassIcon color="warning" />
            <Box>
              <Typography variant="h5" fontWeight={700}>{metrics.pendingImports}</Typography>
              <Typography variant="caption">Pending / Processing Imports</Typography>
            </Box>
          </Paper>
        </Grid>
        <Grid item xs={12} sm={4}>
          <Paper elevation={2} sx={{ p: 2, display: 'flex', alignItems: 'center', gap: 1.5 }}>
            <ErrorIcon color="error" />
            <Box>
              <Typography variant="h5" fontWeight={700}>{metrics.errorImports}</Typography>
              <Typography variant="caption">Error Imports</Typography>
            </Box>
          </Paper>
        </Grid>
      </Grid>

      {/* Source freshness */}
      <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 1 }}>
        Source Freshness
        <Tooltip title="Time of the most recently received file per stream. Null means no file has been ingested yet — this is UNKNOWN, not zero.">
          <span style={{ marginLeft: 6, cursor: 'help', color: '#888' }}>ⓘ</span>
        </Tooltip>
      </Typography>
      <Paper elevation={2} sx={{ p: 0 }}>
        {freshness.map(([stream, ts], i) => (
          <Box key={stream}>
            {i > 0 && <Divider />}
            <Box sx={{ px: 2, py: 1.5, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <Typography variant="body2" fontWeight={600}>{stream}</Typography>
              {ts
                ? <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <CheckCircleIcon sx={{ color: '#2e7d32', fontSize: 18 }} />
                    <Typography variant="caption">{new Date(ts).toLocaleString()}</Typography>
                  </Box>
                : <Chip label="NEVER RECEIVED" size="small" sx={{ bgcolor: '#616161', color: '#fff', fontSize: '0.65rem' }} />
              }
            </Box>
          </Box>
        ))}
      </Paper>
    </Box>
  )
}
