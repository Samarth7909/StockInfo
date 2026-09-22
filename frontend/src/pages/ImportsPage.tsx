import { useEffect, useState } from 'react'
import {
  Box, Typography, Paper, Button, Select, MenuItem,
  FormControl, InputLabel, Alert, CircularProgress,
  Table, TableHead, TableRow, TableCell, TableBody,
  TableContainer, Chip, Stack, LinearProgress,
} from '@mui/material'
import UploadFileIcon from '@mui/icons-material/UploadFile'
import { uploadImport, listImports } from '../api'
import type { ImportRecord, SourceStatus } from '../types'
import { useAuth } from '../AuthContext'

const STREAM_OPTIONS = [
  'HOLDINGS',
  'DP_EXTRACT',
  'CASH_LEDGER',
  'BANK_CONFIRMATION',
  'EXCHANGE_REF',
]

const acceptsExcel = (streamName: string) => streamName === 'BANK_CONFIRMATION'

const STATUS_COLOR: Record<SourceStatus, string> = {
  PENDING:    '#1565c0',
  PROCESSING: '#e65100',
  DONE:       '#2e7d32',
  ERROR:      '#c62828',
}

export default function ImportsPage() {
  const { user }    = useAuth()
  const [imports,   setImports]   = useState<ImportRecord[]>([])
  const [loading,   setLoading]   = useState(false)
  const [error,     setError]     = useState<string | null>(null)
  const [success,   setSuccess]   = useState<string | null>(null)

  // Upload form
  const [streamName, setStreamName] = useState(STREAM_OPTIONS[0])
  const [file,       setFile]       = useState<File | null>(null)
  const [uploading,  setUploading]  = useState(false)
  const [uploadErr,  setUploadErr]  = useState<string | null>(null)

  const load = () => {
    setLoading(true)
    listImports()
      .then(setImports)
      .catch(() => setError('Failed to load imports'))
      .finally(() => setLoading(false))
  }

  useEffect(load, [])

  const handleUpload = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!file) return
    setUploading(true); setUploadErr(null); setSuccess(null)
    try {
      const result = await uploadImport(streamName, file)
      setSuccess(
        result.idempotent
          ? `Identical file already imported (idempotent). Import ID: ${result.id}`
          : `Imported ${result.rowCount} rows, ${result.errorCount} errors. Cases created: ${result.casesCreated}, reopened: ${result.casesReopened}`
      )
      setFile(null)
      load()
    } catch (err: unknown) {
      setUploadErr((err as { response?: { data?: { error?: string } } })?.response?.data?.error ?? 'Upload failed')
    } finally {
      setUploading(false) }
  }

  if (user?.role !== 'OPS_LEAD') {
    return <Alert severity="warning">Import controls are restricted to OPS_LEAD role.</Alert>
  }

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} gutterBottom>File Imports</Typography>

      {/* Upload form */}
      <Paper elevation={3} sx={{ p: 3, mb: 3 }}>
        <Typography variant="subtitle1" fontWeight={700} gutterBottom>Ingest New File</Typography>
        {uploadErr && <Alert severity="error" sx={{ mb: 2 }}>{uploadErr}</Alert>}
        {success   && <Alert severity="success" sx={{ mb: 2 }}>{success}</Alert>}

        <form onSubmit={handleUpload}>
          <Stack direction="row" spacing={2} flexWrap="wrap" alignItems="flex-end">
            <FormControl size="small" sx={{ minWidth: 200 }}>
              <InputLabel>Stream</InputLabel>
              <Select value={streamName} label="Stream"
                onChange={e => setStreamName(e.target.value)}>
                {STREAM_OPTIONS.map(s => <MenuItem key={s} value={s}>{s}</MenuItem>)}
              </Select>
            </FormControl>

            <Button
              variant="outlined"
              component="label"
              startIcon={<UploadFileIcon />}
              size="medium"
            >
              {file ? file.name : 'Choose file'}
              <input
                type="file"
                hidden
                accept={acceptsExcel(streamName) ? '.csv,.xlsx,.xls' : '.csv,.html,.jsonl'}
                onChange={e => setFile(e.target.files?.[0] ?? null)}
                aria-label="File to import"
              />
            </Button>

            <Button
              type="submit"
              variant="contained"
              disabled={uploading || !file}
              sx={{ minWidth: 120 }}
            >
              {uploading ? <CircularProgress size={20} color="inherit" /> : 'Import'}
            </Button>
          </Stack>
        </form>

        <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
          Importing the same file twice is a no-op (SHA-256 idempotency). A corrected file
          creates a new version — previous cases and notes are preserved.
        </Typography>
      </Paper>

      {/* Import history */}
      {error   && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      {loading && <LinearProgress sx={{ mb: 2 }} />}

      <TableContainer component={Paper} elevation={2}>
        <Table size="small" aria-label="Import history">
          <TableHead>
            <TableRow sx={{ '& th': { fontWeight: 700, bgcolor: '#f0f4f8' } }}>
              <TableCell>Stream</TableCell>
              <TableCell>File</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Rows</TableCell>
              <TableCell>Errors</TableCell>
              <TableCell>Cut</TableCell>
              <TableCell>Received</TableCell>
              <TableCell>SHA-256</TableCell>
              <TableCell>Imported By</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {imports.length === 0 && !loading && (
              <TableRow>
                <TableCell colSpan={9} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                  No imports yet.
                </TableCell>
              </TableRow>
            )}
            {imports.map(imp => (
              <TableRow key={imp.id} hover>
                <TableCell>
                  <Chip label={imp.streamName} size="small" variant="outlined" />
                </TableCell>
                <TableCell>
                  <Typography variant="caption">{imp.filename}</Typography>
                </TableCell>
                <TableCell>
                  <Chip
                    label={imp.status}
                    size="small"
                    sx={{ bgcolor: STATUS_COLOR[imp.status], color: '#fff', fontWeight: 700, fontSize: '0.65rem' }}
                  />
                </TableCell>
                <TableCell>{imp.rowCount}</TableCell>
                <TableCell>
                  {imp.errorCount > 0
                    ? <Chip label={imp.errorCount} size="small" color="error" />
                    : <Chip label="0" size="small" color="success" />}
                </TableCell>
                <TableCell>
                  <Typography variant="caption">
                    {imp.cutAt ? new Date(imp.cutAt).toLocaleDateString() : '—'}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Typography variant="caption">{new Date(imp.receivedAt).toLocaleString()}</Typography>
                </TableCell>
                <TableCell>
                  <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                    {imp.sha256Hash.slice(0, 12)}…
                  </Typography>
                </TableCell>
                <TableCell>
                  <Typography variant="caption">{imp.importedBy ?? '—'}</Typography>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  )
}
