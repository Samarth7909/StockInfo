import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Box, Typography, Paper, CircularProgress, Alert, Button,
  Divider, Stack, Chip, TextField, Select, MenuItem,
  FormControl, InputLabel, Accordion, AccordionSummary,
  AccordionDetails, Table, TableHead, TableRow, TableCell,
  TableBody, Tooltip,
} from '@mui/material'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import ArrowBackIcon  from '@mui/icons-material/ArrowBack'
import { getCase, addNote, transitionCase } from '../api'
import type { CaseDetail, CaseState } from '../types'
import SeverityChip from '../components/SeverityChip'
import StateChip    from '../components/StateChip'
import UnknownBadge from '../components/UnknownBadge'
import { useAuth } from '../AuthContext'

const ALLOWED_TRANSITIONS: Record<CaseState, CaseState[]> = {
  OPEN:          ['INVESTIGATING', 'NEEDS_SOURCE', 'RESOLVED'],
  INVESTIGATING: ['NEEDS_SOURCE', 'RESOLVED', 'OPEN'],
  NEEDS_SOURCE:  ['INVESTIGATING', 'RESOLVED', 'OPEN'],
  RESOLVED:      ['REOPENED'],
  REOPENED:      ['INVESTIGATING', 'NEEDS_SOURCE', 'RESOLVED'],
}

export default function CaseDetailPage() {
  const { id }     = useParams<{ id: string }>()
  const navigate   = useNavigate()
  const { user }   = useAuth()
  const [detail,   setDetail]   = useState<CaseDetail | null>(null)
  const [loading,  setLoading]  = useState(true)
  const [error,    setError]    = useState<string | null>(null)

  // Note form
  const [noteBody,     setNoteBody]     = useState('')
  const [noteLoading,  setNoteLoading]  = useState(false)
  const [noteError,    setNoteError]    = useState<string | null>(null)

  // Transition form
  const [targetState,      setTargetState]      = useState<CaseState | ''>('')
  const [transitionReason, setTransitionReason] = useState('')
  const [transLoading,     setTransLoading]     = useState(false)
  const [transError,       setTransError]       = useState<string | null>(null)

  const load = () => {
    if (!id) return
    setLoading(true)
    getCase(id)
      .then(setDetail)
      .catch(() => setError('Case not found or access denied.'))
      .finally(() => setLoading(false))
  }

  useEffect(load, [id])

  const handleAddNote = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!id || !noteBody.trim()) return
    setNoteLoading(true); setNoteError(null)
    try {
      await addNote(id, noteBody.trim())
      setNoteBody('')
      load()
    } catch (err: unknown) {
      setNoteError((err as { response?: { data?: { error?: string } } })?.response?.data?.error ?? 'Failed to add note')
    } finally {
      setNoteLoading(false) }
  }

  const handleTransition = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!id || !targetState || !transitionReason.trim() || !detail) return
    setTransLoading(true); setTransError(null)
    try {
      await transitionCase(id, targetState, transitionReason.trim(), detail.version)
      setTargetState(''); setTransitionReason('')
      load()
    } catch (err: unknown) {
      setTransError((err as { response?: { data?: { error?: string } } })?.response?.data?.error ?? 'Failed to transition case')
    } finally {
      setTransLoading(false) }
  }

  if (loading) return <Box sx={{ display:'flex', justifyContent:'center', mt:8 }}><CircularProgress /></Box>
  if (error || !detail) return <Alert severity="error">{error ?? 'Unknown error'}</Alert>

  const canNote      = user?.role === 'INVESTIGATOR' || user?.role === 'OPS_LEAD'
  const canTransition = user?.role === 'INVESTIGATOR' || user?.role === 'OPS_LEAD'
  const allowedNext  = ALLOWED_TRANSITIONS[detail.state] ?? []

  return (
    <Box>
      {/* Back */}
      <Button startIcon={<ArrowBackIcon />} onClick={() => navigate('/cases')} sx={{ mb: 2 }}>
        Back to Cases
      </Button>

      {/* Header */}
      <Paper elevation={3} sx={{ p: 3, mb: 3 }}>
        <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap" sx={{ mb: 1 }}>
          <SeverityChip severity={detail.severity} />
          <StateChip state={detail.state} />
          <Chip label={detail.caseType.replace(/_/g, ' ')} size="small" variant="outlined" />
          <Typography variant="caption" color="text.secondary">v{detail.version}</Typography>
        </Stack>

        <Typography variant="h6" fontWeight={700} gutterBottom>
          {detail.clientId} — {detail.isin ?? 'N/A'}
        </Typography>

        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          {detail.description}
        </Typography>

        <Stack direction="row" spacing={3} flexWrap="wrap">
          {detail.quantityDelta != null && (
            <Box>
              <Typography variant="caption" color="text.secondary">Quantity Delta</Typography>
              <Typography variant="body1" fontWeight={700}
                color={detail.quantityDelta === 0 ? 'success.main' : 'error.main'}>
                {detail.quantityDelta > 0 ? '+' : ''}{detail.quantityDelta}
              </Typography>
            </Box>
          )}
          <Box>
            <Typography variant="caption" color="text.secondary">Evidence State</Typography>
            <Box>
              {detail.evidenceState === 'UNKNOWN' || detail.evidenceState === 'MISSING_SOURCE'
                ? <UnknownBadge label={detail.evidenceState} />
                : <Chip label={detail.evidenceState} size="small"
                    sx={{ bgcolor: detail.evidenceState === 'MATCHED' ? '#2e7d32' : '#e65100', color: '#fff' }} />}
            </Box>
          </Box>
          <Box>
            <Typography variant="caption" color="text.secondary">Cut At</Typography>
            <Typography variant="body2">{detail.cutAt ? new Date(detail.cutAt).toLocaleString() : 'Unknown'}</Typography>
          </Box>
        </Stack>
      </Paper>

      {/* Evidence Pane */}
      <Accordion defaultExpanded>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography fontWeight={700}>Evidence Pane ({detail.evidence.length} source{detail.evidence.length !== 1 ? 's' : ''})</Typography>
        </AccordionSummary>
        <AccordionDetails>
          {detail.evidence.length === 0
            ? <UnknownBadge label="NO EVIDENCE — SOURCE ABSENT" />
            : (
              <Table size="small">
                <TableHead>
                  <TableRow sx={{ '& th': { fontWeight: 700 } }}>
                    <TableCell>Role</TableCell>
                    <TableCell>Stream</TableCell>
                    <TableCell>File</TableCell>
                    <TableCell>Cut</TableCell>
                    <TableCell>Received</TableCell>
                    <TableCell>SHA-256</TableCell>
                    <TableCell>Mapping</TableCell>
                    <TableCell>Raw Row</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {detail.evidence.map(ev => (
                    <TableRow key={ev.evidenceId}>
                      <TableCell><Chip label={ev.evidenceRole} size="small" variant="outlined" /></TableCell>
                      <TableCell><Typography variant="caption" sx={{ fontFamily: 'monospace' }}>{ev.streamName}</Typography></TableCell>
                      <TableCell><Typography variant="caption">{ev.filename}</Typography></TableCell>
                      <TableCell><Typography variant="caption">{ev.cutAt ? new Date(ev.cutAt).toLocaleDateString() : '—'}</Typography></TableCell>
                      <TableCell><Typography variant="caption">{new Date(ev.receivedAt).toLocaleString()}</Typography></TableCell>
                      <TableCell>
                        <Tooltip title={ev.sha256Hash}>
                          <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>{ev.sha256Hash.slice(0, 12)}…</Typography>
                        </Tooltip>
                      </TableCell>
                      <TableCell><Typography variant="caption">{ev.mappingVersion}</Typography></TableCell>
                      <TableCell>
                        {ev.rawJson
                          ? <Tooltip title={<pre style={{ margin: 0, fontSize: 11 }}>{JSON.stringify(JSON.parse(ev.rawJson), null, 2)}</pre>}>
                              <Typography variant="caption" sx={{ fontFamily: 'monospace', cursor: 'pointer', textDecoration: 'underline dotted' }}>
                                {ev.rawRowId?.slice(0, 8)}…
                              </Typography>
                            </Tooltip>
                          : <Typography variant="caption" color="text.secondary">restricted</Typography>}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
        </AccordionDetails>
      </Accordion>

      {/* State Transition */}
      {canTransition && allowedNext.length > 0 && (
        <Paper elevation={2} sx={{ p: 3, mt: 3 }}>
          <Typography variant="subtitle1" fontWeight={700} gutterBottom>Change State</Typography>
          {transError && <Alert severity="error" sx={{ mb: 1 }}>{transError}</Alert>}
          <form onSubmit={handleTransition}>
            <Stack direction="row" spacing={2} flexWrap="wrap">
              <FormControl size="small" sx={{ minWidth: 180 }}>
                <InputLabel>New State</InputLabel>
                <Select value={targetState} label="New State"
                  onChange={e => setTargetState(e.target.value as CaseState)}>
                  {allowedNext.map(s => (
                    <MenuItem key={s} value={s}
                      disabled={s === 'RESOLVED' && user?.role !== 'OPS_LEAD'}>
                      {s} {s === 'RESOLVED' && user?.role !== 'OPS_LEAD' ? '(OPS_LEAD only)' : ''}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <TextField
                size="small" label="Reason (required)" value={transitionReason}
                onChange={e => setTransitionReason(e.target.value)}
                sx={{ flexGrow: 1, minWidth: 240 }}
                required
              />
              <Button type="submit" variant="contained"
                disabled={transLoading || !targetState || !transitionReason.trim()}>
                {transLoading ? <CircularProgress size={20} /> : 'Apply'}
              </Button>
            </Stack>
          </form>
        </Paper>
      )}

      {/* Notes */}
      <Paper elevation={2} sx={{ p: 3, mt: 3 }}>
        <Typography variant="subtitle1" fontWeight={700} gutterBottom>
          Notes ({detail.notes.length})
        </Typography>
        {detail.notes.map(n => (
          <Box key={n.id} sx={{ mb: 2, p: 2, bgcolor: '#f9fbe7', borderRadius: 1, borderLeft: '3px solid #afb42b' }}>
            <Stack direction="row" justifyContent="space-between">
              <Typography variant="caption" fontWeight={700}>{n.authorUsername}</Typography>
              <Typography variant="caption" color="text.secondary">
                {new Date(n.createdAt).toLocaleString()}
              </Typography>
            </Stack>
            <Typography variant="body2" sx={{ mt: 0.5 }}>{n.body}</Typography>
          </Box>
        ))}
        {detail.notes.length === 0 && (
          <Typography variant="body2" color="text.secondary">No notes yet.</Typography>
        )}

        {canNote && (
          <>
            <Divider sx={{ my: 2 }} />
            {noteError && <Alert severity="error" sx={{ mb: 1 }}>{noteError}</Alert>}
            <form onSubmit={handleAddNote}>
              <TextField
                fullWidth multiline rows={2}
                label="Add note (may explain evidence, must not rewrite source data)"
                value={noteBody} onChange={e => setNoteBody(e.target.value)}
                size="small" sx={{ mb: 1 }}
              />
              <Button type="submit" variant="outlined" size="small"
                disabled={noteLoading || !noteBody.trim()}>
                {noteLoading ? <CircularProgress size={18} /> : 'Save Note'}
              </Button>
            </form>
          </>
        )}
      </Paper>

      {/* Audit trail */}
      <Accordion sx={{ mt: 3 }}>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography fontWeight={700}>State Transition History ({detail.transitions.length})</Typography>
        </AccordionSummary>
        <AccordionDetails>
          {detail.transitions.length === 0
            ? <Typography variant="body2" color="text.secondary">No transitions recorded.</Typography>
            : detail.transitions.map(t => (
              <Box key={t.id} sx={{ mb: 1, display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                <StateChip state={t.fromState} />
                <Typography>→</Typography>
                <StateChip state={t.toState} />
                <Typography variant="caption" color="text.secondary">by {t.byUsername}</Typography>
                <Typography variant="caption" color="text.secondary">
                  at {new Date(t.changedAt).toLocaleString()}
                </Typography>
                <Typography variant="caption">— {t.reason}</Typography>
              </Box>
            ))}
        </AccordionDetails>
      </Accordion>
    </Box>
  )
}
