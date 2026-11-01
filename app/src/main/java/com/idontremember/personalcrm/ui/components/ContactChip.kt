package com.idontremember.personalcrm.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.idontremember.personalcrm.R

@Composable
fun ContactChip(
    name: String,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    if (onRemove != null) {
        InputChip(
            selected = true,
            onClick = onRemove,
            label = { Text(text = "@$name", style = MaterialTheme.typography.labelMedium) },
            trailingIcon = {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_remove_contact),
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = InputChipDefaults.inputChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            modifier = modifier
        )
    } else {
        val chipModifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
        Text(
            text = "@$name",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = (if (onClick != null) chipModifier.clickable(onClick = onClick) else chipModifier)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
