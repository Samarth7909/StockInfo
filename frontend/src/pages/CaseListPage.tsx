import { useEffect, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Box, Typography, Paper, Table, TableHead, TableRow, TableCell,
  TableBody, TableContainer, CircularProgress, Alert,
  FormControl, InputLabel, Select, MenuItem, Pagination,
  Stack, Chip, Tooltip, IconButton,
} from '@mui/material'
import OpenInNewIcon from '@mui/icons-material/OpenInNew'
import { listCases } from '../api'
import type { CaseSummary, CaseSeverity, CaseState } from '../types'
import SeverityChip from '../components/SeverityChip'
import StateChip    from '../components/StateChip'
import UnknownBadge from '../components/UnknownBadge'

const PAGE_SIZE = 50

export default function CaseListPage() {
  const navigate  = useNavigate()
  const [cases,   setCases]   = useState<CaseSummary[]>([])
  const [total,   setTotal]   = useState(0)
  const [pages,   setPages]   = useState(1)
  const [page,    setPage]    = useState(1)
  const [loading, setLoading] = useState(false)
  const [error,   setError]   = useState<string | null>(null)

  // Filters
  const [severity, setSeverity] = useState('')
  const [state,    setState]    = useState('')

  const load = useCallback(() => {
    setLoading(true)
    setError(null)
    listCases({
      page: page - 1,
      size: PAGE_SIZE,
      severity: severity || undefined,
      state:    state    || undefined,
    })
      .then(res => {
        setCases(res.items)
        setTotal(res.totalItems)
        setPages(res.totalPages || 1)
      })
      .catch(() => setError('Failed to load cases'))
      .finally(() => setLoading(false))
  }, [page, severity, state])

  useEffect(() => { load() }, [load])

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} gutterBottom>Exception Cases</Typography>

      {/* Filters */}
      <Stack direction="row" spacing={2} sx={{ mb: 2, flexWrap: 'wrap' }}>
        <FormControl size="small" sx={{ minWidth: 140 }}>
          <InputLabel>Severity</InputLabel>
          <Select value={severity} label="Severity" onChange={e => { setSeverity(e.target.value); setPage(1) }}>
            <MenuItem value="">All</MenuItem>
            {['CRITICAL','HIGH','MEDIUM','LOW'].map(s => <MenuItem key={s} value={s}>{s}</MenuItem>)}
          </Select>
        </FormControl>
        <FormControl size="small" sx={{ minWidth: 160 }}>
          <InputLabel>State</InputLabel>
          <Select value={state} label="State" onChange={e => { setState(e.target.value); setPage(1) }}>
            <MenuItem value="">All</MenuItem>
            {['OPEN','INVESTIGATING','NEEDS_SOURCE','RESOLVED','REOPENED'].map(s =>
              <MenuItem key={s} value={s}>{s}</MenuItem>)}
          </Select>
        </FormControl>
        <Typography variant="caption" color="text.secondary" sx={{ alignSelf: 'center' }}>
          {total} case{total !== 1 ? 's' : ''} found
        </Typography>
      </Stack>

      {error   && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      {loading && <Box sx={{ display: 'flex', justifyContent: 'center', my: 4 }}><CircularProgress /></Box>}

      {!loading && (
        <>
          <TableContainer component={Paper} elevation={2}>
            <Table size="small" aria-label="Exception cases table">
              <TableHead>
                <TableRow sx={{ '& th': { fontWeight: 700, bgcolor: '#f0f4f8' } }}>
                  <TableCell>Severity</TableCell>
                  <TableCell>State</TableCell>
                  <TableCell>Type</TableCell>
                  <TableCell>Client</TableCell>
                  <TableCell>ISIN</TableCell>
                  <TableCell>Delta</TableCell>
                  <TableCell>Evidence</TableCell>
                  <TableCell>Cut</TableCell>
                  <TableCell>Notes</TableCell>
                  <TableCell>Updated</TableCell>
                  <TableCell aria-label="Open case detail" />
                </TableRow>
              </TableHead>
              <TableBody>
                {cases.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={11} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                      No cases match the current filters.
                    </TableCell>
                  </TableRow>
                )}
                {cases.map(c => (
                  <TableRow
                    key={c.id}
                    hover
                    sx={{ cursor: 'pointer' }}
                    onClick={() => navigate(`/cases/${c.id}`)}
                  >
                    <TableCell><SeverityChip severity={c.severity as CaseSeverity} /></TableCell>
                    <TableCell><StateChip state={c.state as CaseState} /></TableCell>
                    <TableCell>
                      <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                        {c.caseType.replace(/_/g, ' ')}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>{c.clientId}</Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>{c.isin ?? '—'}</Typography>
                    </TableCell>
                    <TableCell>
                      {c.quantityDelta != null
                        ? <Chip label={`Δ${c.quantityDelta}`} size="small"
                            sx={{ bgcolor: c.quantityDelta === 0 ? '#2e7d32' : '#c62828', color: '#fff' }} />
                        : '—'}
                    </TableCell>
                    <TableCell>
                      {(c.evidenceState === 'UNKNOWN' || c.evidenceState === 'MISSING_SOURCE')
                        ? <UnknownBadge label={c.evidenceState} />
                        : <Chip label={c.evidenceState} size="small"
                            sx={{ fontSize: '0.65rem',
                                  bgcolor: c.evidenceState === 'MATCHED' ? '#2e7d32' : '#e65100',
                                  color: '#fff' }} />}
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption">
                        {c.cutAt ? new Date(c.cutAt).toLocaleDateString() : '—'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip label={c.noteCount} size="small" variant="outlined" />
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption">{new Date(c.updatedAt).toLocaleDateString()}</Typography>
                    </TableCell>
                    <TableCell onClick={e => e.stopPropagation()}>
                      <Tooltip title="Open detail">
                        <IconButton size="small" onClick={() => navigate(`/cases/${c.id}`)} aria-label="Open case detail">
                          <OpenInNewIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>

          {pages > 1 && (
            <Box sx={{ display: 'flex', justifyContent: 'center', mt: 2 }}>
              <Pagination
                count={pages}
                page={page}
                onChange={(_, v) => setPage(v)}
                color="primary"
                showFirstButton showLastButton
              />
            </Box>
          )}
        </>
      )}
    </Box>
  )
}
