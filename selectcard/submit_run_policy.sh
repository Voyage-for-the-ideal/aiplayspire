#!/usr/bin/env bash
# Submit prep first; afterok prevents GPU allocation on invalid/missing coverage.
set -euo pipefail
stage=pilot
input_dir='STS Data'
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
conda_root=${RUN_POLICY_CONDA_ROOT:-/home/scc/pb23151808/software/miniconda3}
workers=15
min_ascension=0
output_root=''
while (($#)); do
  case "$1" in
    --stage) stage=$2; shift 2 ;;
    --input-dir) input_dir=$2; shift 2 ;;
    --project-dir) project_dir=$2; shift 2 ;;
    --conda-root) conda_root=$2; shift 2 ;;
    --output-root) output_root=$2; shift 2 ;;
    --workers) workers=$2; shift 2 ;;
    --min-ascension) min_ascension=$2; shift 2 ;;
    --help) printf '%s\n' 'submit_run_policy.sh --stage pilot|confirm --input-dir "STS Data" [--min-ascension 0..20] [--workers 15] [--output-root NEW_DIR]'; exit 0 ;;
    *) printf 'Unknown argument: %s\n' "$1" >&2; exit 2 ;;
  esac
done
[[ $stage == pilot || $stage == confirm ]] || { printf 'Invalid stage\n' >&2; exit 2; }
[[ $workers =~ ^[0-9]+$ ]] && ((workers>=1 && workers<=15)) || { printf 'workers must be 1..15\n' >&2; exit 2; }
[[ $min_ascension =~ ^([0-9]|1[0-9]|20)$ ]] || { printf 'min-ascension must be 0..20\n' >&2; exit 2; }
project_dir="$(cd -- "$project_dir" && pwd)"
[[ $input_dir == /* ]] || input_dir="$project_dir/$input_dir"
[[ -d $input_dir ]] || { printf 'Missing input: %s\n' "$input_dir" >&2; exit 2; }
command -v sbatch >/dev/null || { printf 'Slurm sbatch is unavailable\n' >&2; exit 2; }
if [[ -z $output_root ]]; then
  suffix=''
  if ((min_ascension > 0)); then suffix="-a${min_ascension}plus"; fi
  output_root="$project_dir/experiment_results/run_policy/${stage}${suffix}-$(date +%Y%m%d-%H%M%S)-$$"
fi
[[ $output_root == /* ]] || output_root="$project_dir/$output_root"
[[ ! -e $output_root ]] || { printf 'Output exists; choose a new run directory: %s\n' "$output_root" >&2; exit 2; }
mkdir -p -- "$output_root/logs"
prep=$(sbatch --parsable --job-name="policy-${stage}-prep" --cpus-per-task=16 --mem=32G --time=24:00:00 \
  --output="$output_root/logs/prep-%j.log" "$project_dir/run_policy_job.sh" prep "$project_dir" "$input_dir" "$output_root" "$stage" "$conda_root" "$workers" "$min_ascension")
prep=${prep%%;*}
[[ $prep =~ ^[0-9]+$ ]] || { printf 'Invalid prep job ID: %s\n' "$prep" >&2; exit 1; }
gpu=$(sbatch --parsable --job-name="policy-${stage}-train" --dependency="afterok:$prep" --kill-on-invalid-dep=yes \
  --gres=gpu:1 --cpus-per-task=4 --mem=32G --time=24:00:00 \
  --output="$output_root/logs/train-%j.log" "$project_dir/run_policy_job.sh" train "$project_dir" "$input_dir" "$output_root" "$stage" "$conda_root" "$workers")
printf 'CPU preparation: %s\nGPU training (afterok): %s\nArtifacts: %s\n' "$prep" "$gpu" "$output_root"
