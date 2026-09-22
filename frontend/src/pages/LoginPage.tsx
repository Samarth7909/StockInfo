import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Box, Paper, Typography, TextField, Button,
  Alert, CircularProgress,
} from '@mui/material'
import { useAuth } from '../AuthContext'
import { login as apiLogin } from '../api'

export default function LoginPage() {
  const { login } = useAuth()
  const navigate   = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error,    setError]    = useState<string | null>(null)
  const [loading,  setLoading]  = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      const user = await apiLogin(username, password)
      login(user)
      navigate('/dashboard')
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { error?: string } } })
        ?.response?.data?.error ?? 'Invalid credentials'
      setError(msg)
    } finally {
      setLoading(false)
    }
  }

  return (
    <Box
      sx={{
        minHeight: '100vh', display: 'flex',
        alignItems: 'center', justifyContent: 'center',
        background: 'linear-gradient(135deg, #1565c0 0%, #0d47a1 100%)',
      }}
    >
      <Paper elevation={8} sx={{ p: 4, width: '100%', maxWidth: 400, borderRadius: 2 }}>
        <Typography variant="h5" fontWeight={700} gutterBottom>
          Ops Exception Console
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          Stock broker operations desk — authorised access only
        </Typography>

        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

        <form onSubmit={handleSubmit} noValidate>
          <TextField
            label="Username" fullWidth margin="normal" required
            value={username} onChange={e => setUsername(e.target.value)}
            autoComplete="username" autoFocus
            inputProps={{ 'aria-label': 'Username' }}
          />
          <TextField
            label="Password" fullWidth margin="normal" required type="password"
            value={password} onChange={e => setPassword(e.target.value)}
            autoComplete="current-password"
            inputProps={{ 'aria-label': 'Password' }}
          />
          <Button
            type="submit" variant="contained" fullWidth
            sx={{ mt: 2, py: 1.2, fontWeight: 700 }}
            disabled={loading || !username || !password}
          >
            {loading ? <CircularProgress size={22} color="inherit" /> : 'Sign In'}
          </Button>
        </form>

        <Box sx={{ mt: 3, p: 2, bgcolor: '#f5f5f5', borderRadius: 1 }}>
          <Typography variant="caption" display="block" fontWeight={700} gutterBottom>
            Demo credentials
          </Typography>
          {[
            ['opsleader', 'ops123',     'OPS_LEAD'],
            ['invest1',   'invest123',  'INVESTIGATOR'],
            ['support1',  'support123', 'SUPPORT'],
            ['auditor1',  'audit123',   'AUDITOR'],
          ].map(([u, p, r]) => (
            <Typography key={u} variant="caption" display="block" color="text.secondary">
              <strong>{u}</strong> / {p} &nbsp;({r})
            </Typography>
          ))}
        </Box>
      </Paper>
    </Box>
  )
}
