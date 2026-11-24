from enum import Enum


class PickerPhase(Enum):
    SELECT = (True, False)
    SELECT_OR_CONFIRM = (True, True)
    CONFIRM = (False, True)
    SETTLING = (False, False)

    @classmethod
    def from_controls(cls, *, locked: bool, can_confirm: bool) -> "PickerPhase":
        return cls((not locked, can_confirm))

    @property
    def can_select(self) -> bool:
        return self.value[0]

    @property
    def can_confirm(self) -> bool:
        return self.value[1]

    def confirmation_targets(self, selected: tuple[str, ...], preview: str | None) -> tuple[str, ...]:
        if not self.can_confirm:
            raise ValueError("picker is not ready to confirm")
        if self is PickerPhase.CONFIRM and preview is not None:
            return (preview,)
        return selected
