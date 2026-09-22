import { Chip, Tooltip } from '@mui/material'
import HelpOutlineIcon from '@mui/icons-material/HelpOutline'

/** Shown whenever evidence state is UNKNOWN or MISSING_SOURCE. */
export default function UnknownBadge({ label = 'UNKNOWN' }: { label?: string }) {
  return (
    <Tooltip title="Source absent or evidence insufficient — this is UNKNOWN, not zero.">
      <Chip
        icon={<HelpOutlineIcon />}
        label={label}
        size="small"
        sx={{ backgroundColor: '#616161', color: '#fff', fontWeight: 600 }}
      />
    </Tooltip>
  )
}
