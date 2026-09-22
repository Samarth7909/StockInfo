import { Outlet, NavLink, useNavigate } from 'react-router-dom'
import {
  AppBar, Toolbar, Typography, Drawer, List, ListItemButton,
  ListItemIcon, ListItemText, Box, Chip, IconButton, Tooltip,
} from '@mui/material'
import DashboardIcon  from '@mui/icons-material/Dashboard'
import ListAltIcon    from '@mui/icons-material/ListAlt'
import UploadFileIcon from '@mui/icons-material/UploadFile'
import LogoutIcon     from '@mui/icons-material/Logout'
import { useAuth } from '../AuthContext'

const DRAWER_WIDTH = 220

const ROLE_COLORS: Record<string, 'error' | 'warning' | 'success' | 'info' | 'default'> = {
  OPS_LEAD:     'error',
  INVESTIGATOR: 'warning',
  AUDITOR:      'success',
  SUPPORT:      'info',
}

export default function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => { logout(); navigate('/login') }

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      {/* Top bar */}
      <AppBar position="fixed" sx={{ zIndex: t => t.zIndex.drawer + 1 }}>
        <Toolbar>
          <Typography variant="h6" sx={{ flexGrow: 1, fontWeight: 700, letterSpacing: 1 }}>
            Ops Exception Console
          </Typography>
          {user && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Chip
                label={user.role}
                size="small"
                color={ROLE_COLORS[user.role] ?? 'default'}
                sx={{ fontWeight: 700, color: '#fff' }}
              />
              <Typography variant="body2" sx={{ color: 'rgba(255,255,255,0.85)' }}>
                {user.username}
              </Typography>
              <Tooltip title="Log out">
                <IconButton color="inherit" onClick={handleLogout} aria-label="Log out">
                  <LogoutIcon />
                </IconButton>
              </Tooltip>
            </Box>
          )}
        </Toolbar>
      </AppBar>

      {/* Side drawer */}
      <Drawer
        variant="permanent"
        sx={{
          width: DRAWER_WIDTH,
          flexShrink: 0,
          '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box', pt: 8 },
        }}
      >
        <List>
          <SideItem to="/dashboard" icon={<DashboardIcon />}  label="Dashboard" />
          <SideItem to="/cases"     icon={<ListAltIcon />}     label="Cases" />
          {user?.role === 'OPS_LEAD' && (
            <SideItem to="/imports" icon={<UploadFileIcon />} label="Imports" />
          )}
        </List>
      </Drawer>

      {/* Main content */}
      <Box component="main" sx={{ flexGrow: 1, p: 3, pt: 10 }}>
        <Outlet />
      </Box>
    </Box>
  )
}

function SideItem({ to, icon, label }: { to: string; icon: React.ReactNode; label: string }) {
  return (
    <NavLink
      to={to}
      style={({ isActive }) => ({
        backgroundColor: isActive ? 'rgba(21,101,192,0.1)' : undefined,
        borderLeft: isActive ? '3px solid #1565c0' : '3px solid transparent',
        color: 'inherit',
        display: 'block',
        textDecoration: 'none',
      })}
      aria-label={label}
    >
      <ListItemButton>
        <ListItemIcon sx={{ minWidth: 36 }}>{icon}</ListItemIcon>
        <ListItemText primary={label} />
      </ListItemButton>
    </NavLink>
  )
}
