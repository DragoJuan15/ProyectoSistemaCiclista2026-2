# ============================================================
# entrenamiento_V10.py
# SISTEMA CICLISTA - YOLO11
# ============================================================
#
# CAMBIOS PRINCIPALES V10:
# 1. División temporal independiente por cada tarea.
# 2. Separación día/noche.
# 3. Margen entre entrenamiento y validación.
# 4. División cola o bloques.
# 5. Conserva las clases vehiculo, peaton y objetoSolido.
# 6. Reasignación controlada de casiAccidente y peligroReal.
# 7. Informe de eventos reasignados, ambiguos y descartados.
# 8. Evaluación por tarea, día/noche y clase.
# 9. Tabla de precisión y recall por umbral.
# 10. Exportación ONNX opset 12.
# 11. FINAL entrena con todos los datos y usa last.pt.
#
# IMPORTANTE:
# Las métricas de --final NO son independientes porque
# las imágenes de validación también participan en train.
#
# USO:
# python entrenamiento_V10.py --solo-revisar
# python entrenamiento_V10.py --preparar-only
# python entrenamiento_V10.py
# python entrenamiento_V10.py --split bloques
# python entrenamiento_V10.py --final
#
# ============================================================

from pathlib import Path
from collections import Counter
from datetime import datetime
import argparse
import json
import math
import os
import re
import shutil
import sys

import numpy as np
import yaml

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass


# ============================================================
# 1. CONFIGURACIÓN GENERAL
# ============================================================

BASE = Path(__file__).resolve().parent

YOLO_DIR = BASE / "yolo"
DATASETS_DIR = BASE / "new_dataset"
RUNS_DIR = BASE / "runs" / "SistemaCiclista_V10"

VIDEOS_DIA = [2, 4, 6]
VIDEOS_NOCHE = [1, 7, 11, 12]

CLASSES = [
    "vehiculo",
    "peaton",
    "objetoSolido"
]

CLASS_ID = {
    name: index
    for index, name in enumerate(CLASSES)
}

# Clases originales de CVAT:
# 0 vehiculo
# 1 peaton
# 2 bache
# 3 objetoSolido
# 4 casiAccidente
# 5 peligroReal

ID_MAP = {
    0: 0,
    1: 1,
    3: 2
}

EVENT_IDS = {4, 5}

EXPECTED_NAMES = [
    "vehiculo",
    "peaton",
    "bache",
    "objetosolido",
    "casiaccidente",
    "peligroreal"
]

IMAGE_EXTENSIONS = {
    ".jpg", ".jpeg", ".png", ".bmp", ".webp"
}

# Modelo base
MODEL = "yolo11n.pt"

IMGSZ = 960
BATCH = 4
EPOCHS = 100
PATIENCE = 30
WORKERS = 4
DEVICE = 0

# Separación temporal
GAP_COLA = 30
GAP_BLOQUES = 15

# División por bloques
BLOCK_FRAMES = 120
BLOCK_PERIOD = 10
BLOCK_VAL_POSITIONS = (2, 5, 8)

# Evaluación
PRECISION_TARGET = 0.85

CONF_TABLE = [
    0.25, 0.30, 0.40, 0.50,
    0.60, 0.70, 0.80
]

MIN_VAL_BOXES = 30

# Conversión de eventos
IOU_DUPLICATE = 0.50
IOU_AMBIGUOUS = 0.20

INF = 10 ** 9

# Estas reglas se conservan del V3.
# No representan una identificación automática del objeto:
# son asignaciones basadas en la tarea y el número de cuadro.

EVENT_CLASS_RULES = {
    1: [(0, INF, "peaton")],
    2: [(0, 150, "peaton"), (150, INF, "objetoSolido")],
    4: [(0, INF, "vehiculo")],
    6: [(0, INF, "peaton")],
    7: [(0, INF, "objetoSolido")],
    11: [(0, INF, "vehiculo")],
    12: []
}

# Configuración de entrenamiento
TRAIN_KWARGS = dict(
    cos_lr=True,
    close_mosaic=15,
    cls=1.0,
    hsv_v=0.5,
    scale=0.7
)


# ============================================================
# 2. UTILIDADES
# ============================================================

def normalize_name(name):
    return re.sub(r"[^a-z0-9]", "", name.lower())


def natural_key(path):
    parts = re.split(r"(\d+)", path.stem.lower())

    return [
        int(part) if part.isdigit() else part
        for part in parts
    ]


def find_task_directory(task_number):

    if not YOLO_DIR.exists():
        raise RuntimeError(f"No existe la carpeta: {YOLO_DIR}")

    pattern = re.compile(
        rf"task_{task_number}_dataset",
        re.IGNORECASE
    )

    matches = [
        p for p in YOLO_DIR.iterdir()
        if p.is_dir() and pattern.search(p.name)
    ]

    if len(matches) != 1:
        raise RuntimeError(
            f"No se encontró una carpeta única para task_{task_number}.\n"
            f"Coincidencias: {matches}"
        )

    return matches[0]


def find_obj_names(task_dir):

    candidates = list(task_dir.rglob("obj.names"))

    if not candidates:
        raise RuntimeError(
            f"No se encontró obj.names dentro de {task_dir}"
        )

    return candidates[0]


def validate_names(task_dir):

    names_file = find_obj_names(task_dir)

    names = [
        line.strip()
        for line in names_file.read_text(
            encoding="utf-8-sig"
        ).splitlines()
        if line.strip()
    ]

    normalized = [
        normalize_name(name)
        for name in names
    ]

    if normalized != EXPECTED_NAMES:
        raise RuntimeError(
            f"Orden de clases inesperado en {task_dir.name}:\n"
            f"{names}\n"
            f"Se esperaba:\n"
            f"{EXPECTED_NAMES}"
        )


def find_label(image):

    same_folder = image.with_suffix(".txt")

    if same_folder.exists():
        return same_folder

    parts = list(image.parts)

    for i, part in enumerate(parts):

        if part.lower() == "images":

            label_parts = parts.copy()
            label_parts[i] = "labels"

            candidate = Path(*label_parts).with_suffix(".txt")

            if candidate.exists():
                return candidate

    task_root = next(
        (
            p for p in image.parents
            if re.search(
                r"task_\d+_dataset",
                p.name,
                re.I
            )
        ),
        None
    )

    if task_root is None:
        return None

    matches = list(
        task_root.rglob(image.stem + ".txt")
    )

    if len(matches) == 1:
        return matches[0]

    return None


def frame_number(image, position):

    match = re.search(r"(\d+)$", image.stem)

    return int(match.group(1)) if match else position


# ============================================================
# 3. GEOMETRÍA
# ============================================================

def to_xyxy(cx, cy, w, h):

    return (
        cx - w / 2,
        cy - h / 2,
        cx + w / 2,
        cy + h / 2
    )


def iou_xywh(a, b):

    ax1, ay1, ax2, ay2 = to_xyxy(*a)
    bx1, by1, bx2, by2 = to_xyxy(*b)

    inter_w = max(
        0.0,
        min(ax2, bx2) - max(ax1, bx1)
    )

    inter_h = max(
        0.0,
        min(ay2, by2) - max(ay1, by1)
    )

    inter = inter_w * inter_h

    union = (
        a[2] * a[3]
        + b[2] * b[3]
        - inter
    )

    return inter / union if union > 0 else 0.0


def clip_box(cx, cy, w, h):

    x1, y1, x2, y2 = to_xyxy(cx, cy, w, h)

    cx1 = min(max(x1, 0.0), 1.0)
    cy1 = min(max(y1, 0.0), 1.0)
    cx2 = min(max(x2, 0.0), 1.0)
    cy2 = min(max(y2, 0.0), 1.0)

    new_w = cx2 - cx1
    new_h = cy2 - cy1

    if new_w <= 0.001 or new_h <= 0.001:
        return None, True

    changed = (
        abs(cx1 - x1) > 1e-6
        or abs(cy1 - y1) > 1e-6
        or abs(cx2 - x2) > 1e-6
        or abs(cy2 - y2) > 1e-6
    )

    return (
        (
            (cx1 + cx2) / 2,
            (cy1 + cy2) / 2,
            new_w,
            new_h
        ),
        changed
    )


# ============================================================
# 4. LECTURA DE ETIQUETAS
# ============================================================

def parse_label(label_path):

    raw = []

    for line_number, line in enumerate(
        label_path.read_text(
            encoding="utf-8-sig"
        ).splitlines(),
        start=1
    ):

        line = line.strip()

        if not line:
            continue

        values = line.split()

        if len(values) != 5:
            raise RuntimeError(
                f"Etiqueta incorrecta:\n"
                f"{label_path}\n"
                f"Línea: {line_number}"
            )

        original_id = int(float(values[0]))

        if original_id not in range(6):
            raise RuntimeError(
                f"ID desconocido {original_id} en {label_path}"
            )

        cx, cy, w, h = (
            float(value)
            for value in values[1:]
        )

        raw.append(
            (original_id, cx, cy, w, h)
        )

    return raw


def event_class(task, frame):

    for start, end, name in EVENT_CLASS_RULES.get(task, []):

        if start <= frame < end:
            return name

    return None


def new_stats():

    return {
        "eventos": 0,
        "frames_con_evento": 0,
        "duplicadas": 0,
        "ambiguas": 0,
        "sin_clase": 0,
        "recortadas": 0,
        "descartadas": 0,
        "nuevas": Counter()
    }


def convert_frame(task, frame, raw, stats):

    boxes = []
    events = []

    for original_id, cx, cy, w, h in raw:

        if original_id in ID_MAP:

            clipped, changed = clip_box(
                cx, cy, w, h
            )

            if changed:
                stats["recortadas"] += 1

            if clipped is None:
                stats["descartadas"] += 1
                continue

            boxes.append(
                (ID_MAP[original_id], *clipped)
            )

        elif original_id in EVENT_IDS:

            events.append(
                (cx, cy, w, h)
            )

    for cx, cy, w, h in events:

        stats["eventos"] += 1

        clipped, changed = clip_box(
            cx, cy, w, h
        )

        if changed:
            stats["recortadas"] += 1

        if clipped is None:
            stats["descartadas"] += 1
            continue

        best = max(
            (
                iou_xywh(clipped, box[1:])
                for box in boxes
            ),
            default=0.0
        )

        if best >= IOU_DUPLICATE:

            stats["duplicadas"] += 1

        elif best >= IOU_AMBIGUOUS:

            stats["ambiguas"] += 1

        else:

            name = event_class(task, frame)

            if name is None:

                stats["sin_clase"] += 1

            else:

                boxes.append(
                    (CLASS_ID[name], *clipped)
                )

                stats["nuevas"][name] += 1

    return boxes, bool(events)


# ============================================================
# 5. RECOPILACIÓN DE TAREAS
# ============================================================

def print_task_report(task, total, stats):

    print(
        f"\ntask_{task}: {total} imágenes revisadas"
    )

    if stats["eventos"] == 0:

        print(
            "  Sin cajas casiAccidente / peligroReal."
        )

        return

    print(
        f"  Eventos: {stats['eventos']}\n"
        f"  Ya cubiertas por otro objeto: {stats['duplicadas']}\n"
        f"  Agregadas: {dict(stats['nuevas'])}\n"
        f"  Ambiguas: {stats['ambiguas']}\n"
        f"  Sin clase asignable: {stats['sin_clase']}\n"
        f"  Cajas recortadas: {stats['recortadas']}\n"
        f"  Descartadas: {stats['descartadas']}"
    )


def collect_video(task, recording_name):

    task_dir = find_task_directory(task)

    validate_names(task_dir)

    images = [
        p for p in task_dir.rglob("*")
        if p.is_file()
        and p.suffix.lower() in IMAGE_EXTENSIONS
    ]

    images.sort(key=natural_key)

    if not images:
        raise RuntimeError(
            f"No se encontraron imágenes en {task_dir}"
        )

    stats = new_stats()
    records = []

    for position, image in enumerate(images):

        label = find_label(image)

        if label is None:
            raise RuntimeError(
                f"No se encontró TXT para:\n{image}"
            )

        frame = frame_number(
            image,
            position
        )

        boxes, had_event = convert_frame(
            task,
            frame,
            parse_label(label),
            stats
        )

        if had_event:
            stats["frames_con_evento"] += 1

        records.append({
            "recording": recording_name,
            "video": task,
            "image": image,
            "frame": frame,
            "boxes": boxes,
            "had_event": had_event
        })

    print_task_report(
        task,
        len(records),
        stats
    )

    return records


def collect_recordings(tasks, recording_name):

    result = {}

    print(
        f"\n========== RECOPILANDO {recording_name.upper()} =========="
    )

    for task in tasks:

        records = collect_video(
            task,
            recording_name
        )

        result[task] = records

    return result


# ============================================================
# 6. DIVISIÓN TEMPORAL POR TAREA
# ============================================================

def split_tail(records, task):

    total = len(records)

    if total < 20:
        raise RuntimeError(
            f"Task {task}: muy pocas imágenes ({total})."
        )

    boundary = int(total * 0.70)

    train_end = max(
        0,
        boundary - GAP_COLA
    )

    val_start = min(
        total,
        boundary + GAP_COLA
    )

    train = records[:train_end]
    validation = records[val_start:]

    if not train or not validation:
        raise RuntimeError(
            f"Task {task}: división cola vacía."
        )

    return train, validation


def split_blocks(records, task):

    total = len(records)

    if total < 20:
        raise RuntimeError(
            f"Task {task}: muy pocas imágenes ({total})."
        )

    role = ["train"] * total
    val_ranges = []

    for block in range(
        math.ceil(total / BLOCK_FRAMES)
    ):

        if block % BLOCK_PERIOD in BLOCK_VAL_POSITIONS:

            start = block * BLOCK_FRAMES

            end = min(
                total,
                start + BLOCK_FRAMES
            )

            val_ranges.append(
                (start, end)
            )

            for index in range(start, end):
                role[index] = "val"

    for start, end in val_ranges:

        margin = (
            list(
                range(
                    max(0, start - GAP_BLOQUES),
                    start
                )
            )
            +
            list(
                range(
                    end,
                    min(total, end + GAP_BLOQUES)
                )
            )
        )

        for index in margin:

            if role[index] == "train":
                role[index] = "gap"

    train = [
        record
        for record, kind in zip(records, role)
        if kind == "train"
    ]

    validation = [
        record
        for record, kind in zip(records, role)
        if kind == "val"
    ]

    if not train or not validation:
        raise RuntimeError(
            f"Task {task}: división bloques vacía."
        )

    return train, validation


def describe_task_split(task, records, train, validation):

    total = len(records)

    excluded = (
        total
        - len(train)
        - len(validation)
    )

    print(
        f"\nTask {task}:\n"
        f"  Total: {total}\n"
        f"  Train: {len(train)} "
        f"({100 * len(train) / total:.1f} %)\n"
        f"  Val: {len(validation)} "
        f"({100 * len(validation) / total:.1f} %)\n"
        f"  Margen excluido: {excluded}"
    )


def split_recordings(recordings, split_mode):
    """Divide cada grabación completa (día/noche), no cada task por separado.

    Conserva el orden cronológico de VIDEOS_DIA y VIDEOS_NOCHE. Así las
    posiciones de bloques 2, 5 y 8 son relativas a la grabación completa,
    igual que en el script original.
    """

    splitter = (
        split_tail
        if split_mode == "cola"
        else split_blocks
    )

    train_records = []
    val_records = []

    for recording_name, tasks in recordings.items():

        # collect_recordings conserva el orden de la lista de tareas.
        records = []
        for task_records in tasks.values():
            records.extend(task_records)

        train, validation = splitter(
            records,
            recording_name
        )

        total = len(records)
        excluded = total - len(train) - len(validation)

        print(
            f"\nGrabación {recording_name}:\n"
            f"  Total: {total}\n"
            f"  Train: {len(train)} ({100 * len(train) / total:.1f} %)\n"
            f"  Val: {len(validation)} ({100 * len(validation) / total:.1f} %)\n"
            f"  Margen excluido: {excluded}"
        )

        for name, subset in (("Train", train), ("Val", validation)):
            counts = Counter(record["video"] for record in subset)
            detail = ", ".join(
                f"task_{task}: {count}"
                for task, count in sorted(counts.items())
            )
            print(f"  {name} por tarea: {detail or 'ninguna'}")

        train_records.extend(train)
        val_records.extend(validation)

    return train_records, val_records


# ============================================================
# 7. CREACIÓN DEL DATASET YOLO
# ============================================================

def dest_name(record):

    image = record["image"]

    return (
        f"{record['recording']}_task{record['video']}_"
        f"{image.stem}{image.suffix.lower()}"
    )


def link_or_copy(source, destination):

    if destination.exists():
        return

    try:
        os.link(
            source,
            destination
        )

    except OSError:

        shutil.copy2(
            source,
            destination
        )


def write_dataset(records, split_name, output):

    image_dir = (
        output / "images" / split_name
    )

    label_dir = (
        output / "labels" / split_name
    )

    image_dir.mkdir(
        parents=True,
        exist_ok=True
    )

    label_dir.mkdir(
        parents=True,
        exist_ok=True
    )

    class_counts = Counter()
    task_counts = Counter()
    event_frames = 0

    for record in records:

        filename = dest_name(record)

        link_or_copy(
            record["image"],
            image_dir / filename
        )

        lines = [
            (
                f"{box[0]} "
                f"{box[1]:.8f} "
                f"{box[2]:.8f} "
                f"{box[3]:.8f} "
                f"{box[4]:.8f}"
            )
            for box in record["boxes"]
        ]

        label_path = (
            label_dir
            / (Path(filename).stem + ".txt")
        )

        label_path.write_text(
            "\n".join(lines)
            + ("\n" if lines else ""),
            encoding="utf-8"
        )

        class_counts.update(
            CLASSES[box[0]]
            for box in record["boxes"]
        )

        task_counts[record["video"]] += 1

        if record["had_event"]:
            event_frames += 1

    print(
        f"\n========== {split_name.upper()} =========="
    )

    print(f"Imágenes: {len(records)}")
    print(f"Cuadros con eventos: {event_frames}")
    print(f"Instancias: {sum(class_counts.values())}")
    print(f"Clases: {dict(class_counts)}")
    print(f"Imágenes por tarea: {dict(sorted(task_counts.items()))}")

    return class_counts


def write_yaml(output, filename, val_entry):

    yaml_data = {
        "path": str(
            output.resolve()
        ).replace("\\", "/"),

        "train": "images/train",
        "val": val_entry,

        "names": {
            index: name
            for index, name in enumerate(CLASSES)
        }
    }

    yaml_path = output / filename

    with yaml_path.open(
        "w",
        encoding="utf-8"
    ) as file:

        yaml.safe_dump(
            yaml_data,
            file,
            sort_keys=False,
            allow_unicode=True
        )

    return yaml_path


def write_validation_lists(output, val_records):

    result = {}

    groups = {
        "dia": [
            r for r in val_records
            if r["recording"] == "dia"
        ],

        "noche": [
            r for r in val_records
            if r["recording"] == "noche"
        ]
    }

    tasks = sorted(
        set(r["video"] for r in val_records)
    )

    for recording, records in groups.items():

        if not records:
            continue

        files = [
            (
                output
                / "images"
                / "val"
                / dest_name(record)
            ).resolve().as_posix()
            for record in records
        ]

        list_path = (
            output / f"val_{recording}.txt"
        )

        list_path.write_text(
            "\n".join(files) + "\n",
            encoding="utf-8"
        )

        result[recording] = write_yaml(
            output,
            f"data_val_{recording}.yaml",
            list_path.name
        )

    for task in tasks:

        records = [
            r for r in val_records
            if r["video"] == task
        ]

        files = [
            (
                output
                / "images"
                / "val"
                / dest_name(record)
            ).resolve().as_posix()
            for record in records
        ]

        list_path = (
            output / f"val_task_{task}.txt"
        )

        list_path.write_text(
            "\n".join(files) + "\n",
            encoding="utf-8"
        )

        result[f"task_{task}"] = write_yaml(
            output,
            f"data_val_task_{task}.yaml",
            list_path.name
        )

    return result


def prepare_dataset(split_mode, final, dry_run=False):

    day = collect_recordings(
        VIDEOS_DIA,
        "dia"
    )

    night = collect_recordings(
        VIDEOS_NOCHE,
        "noche"
    )

    recordings = {
        "dia": day,
        "noche": night
    }

    print(
        f"\n========== DIVISIÓN {split_mode.upper()} POR GRABACIÓN =========="
    )

    train_records, val_records = split_recordings(
        recordings,
        split_mode
    )

    if final:

        all_records = []

        for tasks in recordings.values():
            for records in tasks.values():
                all_records.extend(records)

        train_records = all_records

        print(
            "\nAVISO: FINAL utilizará todas las imágenes para entrenar."
        )

        print(
            "Las imágenes de validación también estarán en train."
        )

        print(
            "Las métricas de esta ejecución no medirán generalización."
        )

    if not train_records or not val_records:
        raise RuntimeError(
            "La división produjo conjuntos vacíos."
        )

    if dry_run:
        return None

    prefix = (
        "FINAL_V10"
        if final
        else "MIXTO_70_30_V10"
    )

    output = (
        DATASETS_DIR
        / f"{prefix}_{split_mode}"
    )

    if output.exists():

        if (
            DATASETS_DIR.resolve()
            not in output.resolve().parents
        ):
            raise RuntimeError(
                f"Ruta insegura, no se borra: {output}"
            )

        shutil.rmtree(output)

    print(
        "\n========== GENERANDO DATASET =========="
    )

    write_dataset(
        train_records,
        "train",
        output
    )

    val_counts = write_dataset(
        val_records,
        "val",
        output
    )

    if not final:

        for name in CLASSES:

            count = val_counts.get(name, 0)

            if count < MIN_VAL_BOXES:

                print(
                    f"AVISO: pocas cajas de {name} "
                    f"en validación: {count}"
                )

    yaml_path = write_yaml(
        output,
        "data.yaml",
        "images/val"
    )

    val_yamls = write_validation_lists(
        output,
        val_records
    )

    print(
        "\n========== DATASET LISTO =========="
    )

    print(f"Ruta: {output}")
    print(f"Train: {len(train_records)}")
    print(f"Val: {len(val_records)}")
    print(f"YAML: {yaml_path}")

    return {
        "yaml": yaml_path,
        "val_yamls": val_yamls,
        "dir": output
    }


# ============================================================
# 8. EVALUACIÓN
# ============================================================

def run_val(
    best_path,
    data_yaml,
    name,
    args,
    conf=None
):

    from ultralytics import YOLO

    options = dict(
        data=str(data_yaml),
        imgsz=args.imgsz,
        batch=args.batch,
        device=args.device,
        workers=args.workers,
        plots=True,
        project=str(RUNS_DIR),
        name=name,
        exist_ok=True
    )

    if conf is not None:
        options["conf"] = conf

    return YOLO(
        str(best_path)
    ).val(**options)


def summarize(metrics):

    box = metrics.box

    summary = {
        "precision": float(box.mp),
        "recall": float(box.mr),
        "mAP50": float(box.map50),
        "mAP50-95": float(box.map),
        "por_clase": {}
    }

    try:

        for position, index in enumerate(
            box.ap_class_index
        ):

            class_id = int(index)

            name = (
                CLASSES[class_id]
                if class_id < len(CLASSES)
                else str(class_id)
            )

            summary["por_clase"][name] = {
                "precision": float(box.p[position]),
                "recall": float(box.r[position]),
                "mAP50": float(box.ap50[position]),
                "mAP50-95": float(box.ap[position])
            }

    except Exception as error:

        print(
            "AVISO: no se pudo leer la métrica por clase:",
            error
        )

    return summary


def print_summary(title, summary):

    print(
        f"\n{title}\n"
        f"P: {summary['precision']:.3f} | "
        f"R: {summary['recall']:.3f} | "
        f"mAP50: {summary['mAP50']:.3f} | "
        f"mAP50-95: {summary['mAP50-95']:.3f}"
    )

    for name, data in summary["por_clase"].items():

        print(
            f"  {name:>13}: "
            f"P {data['precision']:.3f} | "
            f"R {data['recall']:.3f} | "
            f"mAP50 {data['mAP50']:.3f} | "
            f"mAP50-95 {data['mAP50-95']:.3f}"
        )


def threshold_table(metrics):

    box = metrics.box

    px = getattr(box, "px", None)
    p_curve = getattr(box, "p_curve", None)
    r_curve = getattr(box, "r_curve", None)

    if (
        px is None
        or p_curve is None
        or r_curve is None
        or len(p_curve) == 0
    ):
        return None, None

    px = np.asarray(px)
    p_curve = np.asarray(p_curve)
    r_curve = np.asarray(r_curve)

    def at(conf):

        index = int(
            np.argmin(
                np.abs(px - conf)
            )
        )

        return (
            float(np.mean(p_curve[:, index])),
            float(np.mean(r_curve[:, index]))
        )

    rows = []

    for conf in CONF_TABLE:

        precision, recall = at(conf)

        rows.append({
            "conf": conf,
            "precision": precision,
            "recall": recall
        })

    grid = [
        float(c)
        for c in np.arange(0.05, 0.96, 0.01)
    ]

    candidates = []

    for conf in grid:

        precision, recall = at(conf)

        if (
            precision >= PRECISION_TARGET
            and recall > 0
        ):
            candidates.append(
                (recall, conf, precision)
            )

    if candidates:

        recall, conf, precision = max(
            candidates
        )

        recommended = {
            "conf": round(conf, 2),
            "precision": precision,
            "recall": recall,
            "reaches_target": True
        }

    else:

        def f1(conf):

            precision, recall = at(conf)

            return (
                2 * precision * recall
                / (precision + recall + 1e-9)
            )

        conf = max(
            grid,
            key=f1
        )

        precision, recall = at(conf)

        recommended = {
            "conf": round(conf, 2),
            "precision": precision,
            "recall": recall,
            "reaches_target": False
        }

    return rows, recommended


def evaluate(best_path, data, run_name, args):

    print(
        "\n========== EVALUACIÓN INDEPENDIENTE =========="
    )

    metrics = run_val(
        best_path,
        data["yaml"],
        run_name + "_val",
        args
    )

    summary = summarize(metrics)

    print_summary(
        "VALIDACIÓN COMPLETA",
        summary
    )

    rows, recommended = threshold_table(
        metrics
    )

    if rows:

        print(
            "\n========== UMBRALES DE CONFIANZA =========="
        )

        for row in rows:

            print(
                f"Conf {row['conf']:.2f}: "
                f"P {row['precision']:.3f} | "
                f"R {row['recall']:.3f}"
            )

        if recommended["reaches_target"]:

            print(
                f"\nUmbral recomendado: {recommended['conf']}\n"
                f"Precisión: {recommended['precision']:.3f}\n"
                f"Recall: {recommended['recall']:.3f}"
            )

        else:

            print(
                "\nNo se alcanzó la precisión objetivo.\n"
                f"Mejor equilibrio F1: {recommended['conf']}\n"
                f"Precisión: {recommended['precision']:.3f}\n"
                f"Recall: {recommended['recall']:.3f}"
            )

    per_group = {}

    for recording, yaml_path in data["val_yamls"].items():

        try:

            partial_metrics = run_val(
                best_path,
                yaml_path,
                f"{run_name}_val_{recording}",
                args
            )

            partial = summarize(
                partial_metrics
            )

            per_group[recording] = partial

            print_summary(
                recording.upper(),
                partial
            )

        except Exception as error:

            print(
                f"AVISO: no se pudo evaluar {recording}: {error}"
            )

    operating_conf = (
        args.conf_detector
        if args.conf_detector is not None
        else (
            recommended["conf"]
            if recommended
            else 0.40
        )
    )

    try:

        run_val(
            best_path,
            data["yaml"],
            f"{run_name}_matriz_conf{operating_conf:.2f}",
            args,
            conf=operating_conf
        )

    except Exception as error:

        print(
            "AVISO: no se pudo generar la matriz:",
            error
        )

    report = {
        "validacion": summary,
        "tabla_umbral": rows,
        "umbral_recomendado": recommended,
        "por_grabacion": per_group
    }

    return report


# ============================================================
# 9. EXPORTACIÓN ONNX
# ============================================================

def export_onnx(weights_path, args):

    from ultralytics import YOLO

    print(
        "\n========== EXPORTANDO ONNX =========="
    )

    output = YOLO(
        str(weights_path)
    ).export(
        format="onnx",
        imgsz=args.imgsz,
        opset=12,
        simplify=True,
        dynamic=False,
        half=False,
        nms=False
    )

    return Path(output)


def write_model_info(onnx_path, args, report):

    recommended = (
        report or {}
    ).get("umbral_recomendado")

    if args.conf_detector is not None:

        conf = args.conf_detector

    elif recommended and not args.final:

        conf = recommended["conf"]

    else:

        conf = None

    summary = (
        report or {}
    ).get("validacion") or {}

    info = {
        "version": "V10",
        "modelo": args.model,
        "imgsz": args.imgsz,
        "clases": CLASSES,
        "split": args.split,
        "final": bool(args.final),
        "conf_recomendado": conf,

        "metricas_independientes": (
            not args.final
        ),

        "metricas": {
            key: summary.get(key)
            for key in (
                "precision",
                "recall",
                "mAP50",
                "mAP50-95"
            )
        }
    }

    info_path = (
        onnx_path.parent
        / "modelo_info.json"
    )

    info_path.write_text(
        json.dumps(
            info,
            indent=2,
            ensure_ascii=False
        ),
        encoding="utf-8"
    )

    print(
        "\nArchivos para el detector:"
    )

    print(onnx_path)
    print(info_path)


# ============================================================
# 10. ENTRENAMIENTO
# ============================================================

def train_model(data, args):

    from ultralytics import YOLO

    run_name = (
        f"V10_{Path(args.model).stem}_{args.split}"
        + ("_FINAL" if args.final else "")
        + "_"
        + datetime.now().strftime("%Y%m%d_%H%M%S")
    )

    print(
        "\n========== ENTRENAMIENTO V10 =========="
    )

    print(f"Modelo: {args.model}")
    print(f"Resolución: {args.imgsz}")
    print(f"Batch: {args.batch}")
    print(f"Épocas: {args.epochs}")
    print(f"Paciencia: {args.patience}")
    print(f"Dispositivo: {args.device}")

    model = YOLO(
        args.model
    )

    results = model.train(
        data=str(data["yaml"]),
        epochs=args.epochs,
        patience=args.patience,
        imgsz=args.imgsz,
        batch=args.batch,
        device=args.device,
        workers=args.workers,
        project=str(RUNS_DIR),
        name=run_name,
        exist_ok=False,
        pretrained=True,
        optimizer="auto",
        verbose=True,
        plots=True,
        save=True,
        seed=42,
        **TRAIN_KWARGS
    )

    trainer = getattr(
        model,
        "trainer",
        None
    )

    save_dir = Path(
        getattr(
            trainer,
            "save_dir",
            None
        )
        or results.save_dir
    )

    weights_dir = (
        save_dir / "weights"
    )

    best_path = (
        weights_dir / "best.pt"
    )

    last_path = (
        weights_dir / "last.pt"
    )

    if args.final:

        # FINAL: todos los datos participaron en entrenamiento.
        # Se utiliza last.pt para no seleccionar el modelo mediante
        # una validación que comparte imágenes con train.

        weights_path = last_path

        print(
            "\nFINAL: se utilizará last.pt."
        )

        print(
            "Las métricas internas de entrenamiento no son "
            "una evaluación independiente."
        )

        report = None

    else:

        if not best_path.exists():

            raise RuntimeError(
                f"No se encontró best.pt: {best_path}"
            )

        weights_path = best_path

        print(
            "\n========== EVALUACIÓN V10 =========="
        )

        report = evaluate(
            weights_path,
            data,
            run_name,
            args
        )

        report_path = (
            save_dir / "resumen_v10.json"
        )

        report_path.write_text(
            json.dumps(
                report,
                indent=2,
                ensure_ascii=False
            ),
            encoding="utf-8"
        )

    if not weights_path.exists():

        raise RuntimeError(
            f"No se encontró el peso final: {weights_path}"
        )

    print(
        f"\nPesos seleccionados: {weights_path}"
    )

    onnx_path = export_onnx(
        weights_path,
        args
    )

    write_model_info(
        onnx_path,
        args,
        report
    )

    print(
        "\n========== V10 TERMINADO =========="
    )


# ============================================================
# 11. PROGRAMA PRINCIPAL
# ============================================================

def main():

    parser = argparse.ArgumentParser(
        description="Entrenamiento V10 - Sistema Ciclista"
    )

    parser.add_argument(
        "--solo-revisar",
        action="store_true"
    )

    parser.add_argument(
        "--preparar-only",
        action="store_true"
    )

    parser.add_argument(
        "--split",
        choices=["cola", "bloques"],
        default="cola"
    )

    parser.add_argument(
        "--final",
        action="store_true"
    )

    parser.add_argument(
        "--model",
        default=MODEL
    )

    parser.add_argument(
        "--imgsz",
        type=int,
        default=IMGSZ
    )

    parser.add_argument(
        "--batch",
        type=int,
        default=BATCH
    )

    parser.add_argument(
        "--epochs",
        type=int,
        default=EPOCHS
    )

    parser.add_argument(
        "--patience",
        type=int,
        default=None
    )

    parser.add_argument(
        "--workers",
        type=int,
        default=WORKERS
    )

    parser.add_argument(
        "--device",
        default=str(DEVICE)
    )

    parser.add_argument(
        "--conf-detector",
        type=float,
        default=None
    )

    args = parser.parse_args()

    if args.patience is None:
        args.patience = args.epochs

    if args.epochs < 1:
        parser.error("--epochs debe ser mayor que cero.")

    if args.batch < 1:
        parser.error("--batch debe ser mayor que cero.")

    if args.imgsz < 32:
        parser.error("--imgsz no es válido.")

    if (
        args.conf_detector is not None
        and not 0 <= args.conf_detector <= 1
    ):
        parser.error(
            "--conf-detector debe estar entre 0 y 1."
        )

    if args.solo_revisar:

        print(
            "\nMODO REVISIÓN: no se generará ni modificará el dataset."
        )

        prepare_dataset(
            args.split,
            args.final,
            dry_run=True
        )

        print(
            "\nRevisión terminada."
        )

        return

    data = prepare_dataset(
        args.split,
        args.final
    )

    if args.preparar_only:

        print(
            "\nPreparación terminada. No se inició entrenamiento."
        )

        return

    train_model(
        data,
        args
    )


if __name__ == "__main__":

    main()