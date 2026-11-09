"""Build fresh ascension-filtered outcomes and enrich visible bosses."""
import argparse
from pathlib import Path
try:
    from .config import Config
    from .data_pipeline import build_dataset
    from .generate_visible_boss_dataset import generate
except ImportError:
    from config import Config
    from data_pipeline import build_dataset
    from generate_visible_boss_dataset import generate


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input-dir",required=True)
    parser.add_argument("--output-dir")
    parser.add_argument("--workers",type=int,default=None)
    parser.add_argument("--minimum-boss-samples",type=int,default=100)
    parser.add_argument("--min-ascension",type=int,choices=range(21),default=10)
    args = parser.parse_args(argv)
    if args.output_dir is None and args.min_ascension != 10:
        parser.error("--output-dir is required when --min-ascension is not 10")
    output = Path(args.output_dir or Config.DATA_DIR)
    base = output.with_name(output.name+"_base")
    audit = output.with_name(output.name+"_boss_audit.csv")
    for path in (output,base,audit):
        if path.exists():
            raise FileExistsError(f"Refusing to replace existing artifact: {path}")
    build_dataset(args.input_dir,base,workers=args.workers,min_ascension=args.min_ascension)
    generate(args.input_dir,str(base),str(output),str(audit),args.minimum_boss_samples)


if __name__ == "__main__":
    main()
