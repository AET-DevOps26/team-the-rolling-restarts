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

{{/* Standard Kubernetes labels for a workload. */}}
{{- define "newsGenAI.labels" -}}
app: {{ .name }}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .releaseName }}
app.kubernetes.io/managed-by: Helm
app.kubernetes.io/part-of: newsGenAI
{{- end -}}