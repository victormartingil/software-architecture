"""Remove exception payloads from Uvicorn's HTTP error output."""
import logging


class ServerErrorFilter(logging.Filter):
    def filter(self, record):
        if record.levelno >= logging.ERROR or record.exc_info or record.exc_text or record.stack_info:
            exception = record.exc_info[1] if record.exc_info else None
            error_type = type(exception).__name__ if exception is not None else "ServerError"
            record.msg = f"ASGI server error ({error_type})"
            record.args = ()
            record.exc_info = None
            record.exc_text = None
            record.stack_info = None
        return True


def install_server_log_filter():
    """Install once on the emitting logger, independently of OTLP configuration."""
    logger = logging.getLogger("uvicorn.error")
    if not any(isinstance(item, ServerErrorFilter) for item in logger.filters):
        logger.addFilter(ServerErrorFilter())
