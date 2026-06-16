{{/* Return the fully qualified image for a workload. */}}
{{- define "newsGenAI.image" -}}
{{- $global := .global -}}
{{- $app := .app -}}
{{- if $app.image -}}
{{- $app.image -}}
{{- else -}}
{{- printf "%s/%s:%s" $global.registry $app.imageName $global.tag -}}
{{- end -}}
{{- end -}}