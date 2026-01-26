import numpy as np

def resample_points(points, n=32):
    """Resamples the path into N evenly spaced points to ignore drawing speed."""
    pts = np.array(points)
    if len(pts) < 2: return np.zeros((n, 2))
    
    # Calculate distances between consecutive points
    deltas = np.diff(pts, axis=0)
    dist = np.sqrt((deltas**2).sum(axis=1))
    path_len = dist.sum()
    if path_len == 0: return np.zeros((n, 2))
    
    # Create evenly spaced distances along the path
    interp_dists = np.linspace(0, path_len, n)
    cum_dists = np.concatenate(([0], np.cumsum(dist)))
    
    # Interpolate to get new points
    new_pts = np.zeros((n, 2))
    for i in range(2):
        new_pts[:, i] = np.interp(interp_dists, cum_dists, pts[:, i])
    return new_pts

def normalize_points(points):
    """Scales the gesture to a 100x100 bounding box."""
    pts = resample_points(points)
    pts = pts - pts.min(axis=0)
    span = pts.max(axis=0) - pts.min(axis=0)
    # Avoid division by zero if it's a straight line
    scale = np.where(span == 0, 1, span)
    return (pts / scale) * 100

def get_similarity(p1, p2):
    """Calculates the average Euclidean distance between two paths."""
    return np.mean(np.linalg.norm(p1 - p2, axis=1))
