#!/system/bin/sh
# Metadata only. Do not open trace or trace_pipe_raw, enable events, or load a payload.

printf 'Read-only Shizuku preflight\n'
printf 'identity: '
id
printf 'shell_context: '
if test -r "/proc/$$/attr/current"; then
    cat "/proc/$$/attr/current"
    printf '\n'
else
    printf 'unreadable\n'
fi
printf 'model: '
getprop ro.product.model
printf 'firmware: '
getprop ro.build.PDA
printf 'display_id: '
getprop ro.build.display.id
printf 'kernel: '
uname -r

trace_root=/sys/kernel/tracing
event_root="$trace_root/events/sched/sched_blocked_reason"
printf 'event_id: '
if test -r "$event_root/id"; then
    cat "$event_root/id"
    printf '\n'
else
    printf 'unreadable or unavailable\n'
fi

for path in "$trace_root/tracing_on" "$event_root/enable" "$trace_root/trace"; do
    present=unavailable
    readable=no
    writable=no
    if test -e "$path"; then present=yes; fi
    if test -r "$path"; then readable=yes; fi
    if test -w "$path"; then writable=yes; fi
    printf '%s present=%s readable=%s writable=%s\n' \
        "$path" "$present" "$readable" "$writable"
done

raw_present=0
raw_readable=0
for path in "$trace_root"/per_cpu/cpu*/trace_pipe_raw; do
    if test -e "$path"; then
        raw_present=$((raw_present + 1))
        if test -r "$path"; then raw_readable=$((raw_readable + 1)); fi
    fi
done
printf 'raw_trace_metadata: visible=%s apparently_readable=%s\n' \
    "$raw_present" "$raw_readable"
printf 'Permission metadata does not prove that later open/write operations will succeed.\n'
printf 'No trace buffers were read. No tracing controls were written. No root payload was run.\n'
